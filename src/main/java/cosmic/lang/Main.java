/* Version 2-10-26-15--This version has less choppy animation due to forcing the
 * use of high-precision timer due to using Thread.sleep, not a Timer object
 * Updated 9-17-2019.  Works very well.
 * Updated 3-9-2020.  There were problems with getMouseY() and added ability to
 * check specific mouse buttons.
 */
package cosmic.lang;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.swing.*;
import static java.awt.event.KeyEvent.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

// main is where the action starts:  it sets things up
public class Main {
    public static void main(String[] args) {
        JFrame mainWindow = new JFrame();
        mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainWindow.setPreferredSize(new Dimension(MainGame.WINDOW_WIDTH, MainGame.WINDOW_HEIGHT));
        mainWindow.setUndecorated(!MainGame.SHOW_TITLE_BAR);

        GameBase game = new MainGame(mainWindow);
        game.SET = true;  // set this to true AFTER MainGame constructor finishes
        // to enable update and paintComponent methods to be called by Timer
        mainWindow.add(game);
        mainWindow.setLocation(50, 25);
        mainWindow.pack();
        mainWindow.setVisible(true);
        game.start();
    }
}

class KeyTracker implements KeyListener {

    final private boolean[] keyPressed;
    final private boolean[] keyDown;

    KeyTracker(JComponent parent) {
        keyDown = new boolean[256];
        keyPressed = new boolean[256];
        parent.addKeyListener(this);
        parent.setFocusable(true);
        parent.requestFocusInWindow();
    }

    @Override
    public void keyTyped(KeyEvent e) { }

    @Override
    public void keyPressed(KeyEvent e) {
        int keycode = e.getKeyCode();
        if (keycode >= 0 && keycode < 256) {
            keyPressed[keycode] = true;
            keyDown[keycode] = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int keycode = e.getKeyCode();
        if (keycode >= 0 && keycode < 256) {
            keyDown[keycode] = false;
        }
    }

    public void resetKeys() {
        for (int i = 0; i < 256; i++) {
            keyPressed[i] = false;
        }
    }

    public boolean isKeyDown(int keycode) {
        return (keycode >= 0 && keycode < 256) ? keyDown[keycode] : false;
    }

    public boolean wasKeyPressed(int keycode) {
        return (keycode >= 0 && keycode < 256) ? keyPressed[keycode] : false;
    }
}


abstract class GameBase extends JComponent {

    // technical details:

    private JFrame window;
    protected KeyTracker keys;
    private java.util.Timer updateTimer;
    public boolean SET;  // used so that update and paintComponent are not called until MainGame constructor finishes
    private int mouseX, mouseY;

    private boolean middleMouseDown, middleMouseWasPressed;
    private boolean leftMouseDown, leftMouseWasPressed;
    private boolean rightMouseDown, rightMouseWasPressed;
    private int frameCount;

    public static final int LEFT_MOUSE_BUTTON = 0, MIDDLE_MOUSE_BUTTON = 1, RIGHT_MOUSE_BUTTON = 2;
    public boolean isMouseButtonDown(int button) {
        switch (button) {
            case LEFT_MOUSE_BUTTON: return leftMouseDown;
            case MIDDLE_MOUSE_BUTTON: return middleMouseDown;
            case RIGHT_MOUSE_BUTTON: return rightMouseDown;
            default: return false;
        }
    }
    public boolean wasMouseButtonPressed(int button) {
        switch (button) {
            case LEFT_MOUSE_BUTTON: return leftMouseWasPressed;
            case MIDDLE_MOUSE_BUTTON: return middleMouseWasPressed;
            case RIGHT_MOUSE_BUTTON: return rightMouseWasPressed;
            default: return false;
        }
    }
    public boolean isKeyDown(int keycode) {
        return keys.isKeyDown(keycode);
    }
    public boolean wasKeyPressed(int keycode) {
        return keys.wasKeyPressed(keycode);
    }
    public int getMouseX() {
        return mouseX;
    }
    public int getMouseY() {
        return mouseY;
    }
    public GameBase(JFrame window) {
        leftMouseDown = false;
        middleMouseDown = false;
        rightMouseDown = false;
        leftMouseWasPressed = false;
        middleMouseWasPressed = false;
        rightMouseWasPressed = false;
        frameCount = 0;
        keys = new KeyTracker(this);
        this.window = window;

        this.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // yes, need this so mouseX/Y still update when button is held down
                mouseX = e.getX();
                mouseY = e.getY();
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
            }
        });
        this.addMouseListener(new MouseListener() {

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    leftMouseDown = true;
                    leftMouseWasPressed = true;
                } else if (e.getButton() == java.awt.event.MouseEvent.BUTTON2) {
                    middleMouseDown = true;
                    middleMouseWasPressed = true;
                } else if (e.getButton() == java.awt.event.MouseEvent.BUTTON3) {
                    rightMouseDown = true;
                    rightMouseWasPressed = true;
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    leftMouseDown = false;
                    leftMouseWasPressed = false;
                } else if (e.getButton() == java.awt.event.MouseEvent.BUTTON2) {
                    middleMouseDown = false;
                    middleMouseWasPressed = false;
                } else if (e.getButton() == java.awt.event.MouseEvent.BUTTON3) {
                    rightMouseDown = false;
                    rightMouseWasPressed = false;
                }
            }
            @Override
            public void mouseClicked(MouseEvent e) {}
            @Override
            public void mouseEntered(MouseEvent e) {}
            @Override
            public void mouseExited(MouseEvent e) {}
        });
        // Using a Thread.sleep makes smoother animation than using either java.util.Timer
        // or javax.swing.Timer.
        // Yes, for this obscure reason (seems to be Windows-only?):
        // 'Having a sleeping thread forces the VM to use the high-precision timer.'
        // --http://www.java-gaming.org/index.php/topic,24112.
        // without this, the animation is choppy.  Ugh.
        updateTimer = new java.util.Timer();
        java.util.TimerTask myTask = new java.util.TimerTask() {
            @Override
            public void run() {
                if (SET) {
                    update();
                    frameCount++;
                    keys.resetKeys();
                    leftMouseWasPressed = false;
                    middleMouseWasPressed = false;
                    rightMouseWasPressed = false;
                    repaint();
                    Toolkit.getDefaultToolkit().sync();  // no idea what this does
                    // but BOY does it fix choppiness
                    // SEE http://zetcode.com/javagames/animation/
                }
            }
        };
        updateTimer.scheduleAtFixedRate(myTask, 0, 16);

    }
    public void start() {
        // updateTimer.
    }
    // this is only needed because we can't 'X' out

    public void quit() {
        window.dispose();
        updateTimer.cancel();
    }
    public Clip loadClip(String filename) {
        Clip audioClip;
        try {
            File wavFile = new File(filename);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(wavFile);
            audioClip = AudioSystem.getClip();
            audioClip.open(audioStream);
            return audioClip;
        } catch (Exception e) {
            System.out.println("woops...sound issue...can't load " + filename);
            return null;
        }
    }
    public Image loadImage(String filename) {
        BufferedImage image;
        try {
            File imgFile = new File(filename);
            image = ImageIO.read(imgFile);
            return image;
        } catch (Exception e) {
            System.out.println("woops...can't load " + filename);
            return null;
        }
    }
    public void playClip(Clip c) {
        if (c != null) {
            c.setFramePosition(0);
            c.start();   // start playback, play once
        }
    }
    public int getFrameCount() {
        return frameCount;
    }
    // YOU need to write this method:
    public abstract void update();
}

/* END BOILERPLATE */

class ImageLoader {
    public static int[][] loadImage(String filename) {
        // First we load a file into a 2-d array:
        // File myFile = new File("Giraffes.bmp");

        BufferedImage image;
        try {
            image = ImageIO.read(Main.class.getResourceAsStream(filename));
        } catch (IOException e) {
            System.out.println(e);
            return null;
        }
        int height = image.getHeight();
        int width  = image.getWidth();
        int[][] pixels = new int[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                pixels[i][j] = image.getRGB(j, i);
            }
        }
        return pixels;
    }
}



class World {
    public static final int SIZE     = 5;         // size, in pixels, of one 'block' in the world
    public static final int PX_METER = SIZE * 4;  // # pixels drawn on screen per meter

    private int[][]  blocks;
    private double   centerX, centerY;  // 'center' used to check for lap completion.
    private double   startX, startY;    // used to set starting positions
    public World(String imageFilename) {
        // Load a world from an image.  Hopefully the right height.
        // Colors:
        //     White (255, 255, 255)...empty space
        //     Blue  (0,   128, 255)...start position
        //     Magenta (200, 0, 200)...center for lap checks
        //     Grey  (64, 64, 64)   ...solid.
        int[][] pixels = ImageLoader.loadImage(imageFilename);
        int     height = pixels.length;
        int     width  = pixels[0].length;
        blocks = new int[height][width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int px = pixels[i][j];
                if ((px & 255) == 255) {
                    // If low byte is 255, it's empty.
                    blocks[i][j] = 0;
                } else {
                    // Otherwise, it's solid..
                    blocks[i][j] = 1;
                    if ((px & 255) != 64) {
                        System.out.println(px & 255);
                    }
                }
                if (((px >> 8) & 255) == 128) {
                    // If mid byte is 128, it's the starting position.
                    // RGB (0, 128, 255)
                    startX = ((double)j) * SIZE / PX_METER;
                    startY = ((double)i) * SIZE / PX_METER;
                }
                if ((px & 255) == 200) {
                    // If low byte is 200, it's the centerX, centerY (AND solid).
                    // RGB (200, 0, 200)
                    centerX = ((double)j) * SIZE / PX_METER;
                    centerY = ((double)i) * SIZE / PX_METER;
                }

            }
        }
        System.out.println("Center is at " + centerX + ", " + centerY + ".");
        System.out.println("Start is at " + startX + ", " + startY + ".");
    }
    public double getStartX() {
        return startX;
    }
    public double getStartY() {
        return startY;
    }
    public void draw(Graphics g) {
        g.setColor(Color.BLACK);
        for (int i = 0; i < blocks.length; i++) {
            for (int j = 0; j < blocks[0].length; j++) {
                if (blocks[i][j] == 1) {
                    g.fillRect(j * SIZE, i * SIZE, SIZE, SIZE);
                }
            }
        }
    }
    public boolean checkWorldPoint(double x, double y) {
        return checkScreenPoint(PX_METER * x, PX_METER * y);
    }
    public boolean checkScreenPoint(double x, double y) {
        int i = (int)(y / SIZE);
        int j = (int)(x / SIZE);
        if (i >= 0 && j >= 0 && i < blocks.length && j < blocks[0].length) {
            return (blocks[i][j] == 1);
        } else {
            return false;
        }
    }
    public final int getQuadrant(double x0, double y0) {
        if (x0 >= centerX && y0 <= centerY) {
            return 0;
        } else if (x0 >= centerX && y0 > centerY) {
            return 1;
        } else if (x0 < centerX && y0 > centerY) {
            return 2;
        } else {
            return 3;
        }
    }
}

class Particle {
    private int counter;
    private double x, y, vx, vy;
    public Particle(double x, double y, double vx, double vy, int lifetime) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.counter = lifetime;
    }
    public void update() {
        x += vx * Bot.DT;
        y += vy * Bot.DT;
        counter--;
        vx *= .98;
        vy *= .98;
    }
    public boolean isExpired() {
        return (counter <= 0);
    }

    public void draw(Graphics g) {
        int size = 5;
        if (counter > 50) {
            g.setColor(Color.WHITE);
        } else if (counter > 40) {
            g.setColor(Color.YELLOW);
        } else if (counter > 40) {
            g.setColor(Color.ORANGE);
        } else if (counter > 30) {
            g.setColor(Color.RED);
            size = 4;
        } else if (counter > 20) {
            g.setColor(Color.DARK_GRAY);
            size = 3;
        } else {
            g.setColor(Color.BLACK);
            size = 2;
        }
        g.fillRect((int)(World.PX_METER * x - size / 2.0), (int)(World.PX_METER * y - size / 2.0), size, size);
    }
}

class Bot {
    // Units will be meters, meters per second, etc.
    public static final double RADIUS     = 0.6;              // Bot radius, in meters
    public static final double DT         = 1.0 / 60.0;       // Time per frame (assumed 60 fps)
    public static final double RAD_TO_DEG = 180.0 / Math.PI;  // Conversion factor, radians to degrees
    public static final double DEG_TO_RAD = Math.PI / 180.0;  // Convert degrees to radians
    public static final double MAX_POWER  = 25.0;             // Max engine "power"
    public static final double MAX_ACCEL  = 25.0;             // Max acceleration, limited by tire traction
    public static final double R_MIN      = 0.5;              // Minimum turn radius
    public static final double SCAN_INCREMENT = 0.25;         // when we scan, how far do we step each check? (meters)
    public static final double SCAN_MAX_DIST  = 50.0;         // when we can, what's the furthest out we check?
    public static final int    START_HEALTH   = 3;

    // These are used in the getAction method for selecting an action:
    public static final int
            DRIFT = 0, ACCEL = 1, BRAKE = 2, LEFT  = 3, RIGHT = 4,
            ACCEL_RIGHT = 5, BRAKE_RIGHT = 6, ACCEL_LEFT = 7, BRAKE_LEFT = 8,
            SLIGHT_RIGHT = 9, SLIGHT_LEFT = 10;

    // Used when checking for crash:
    public static final double INCREMENTS[] = {
            RADIUS, 0,   RADIUS * .7,  RADIUS * .7,
            0, RADIUS,  -RADIUS * .7,  RADIUS * .7,
            -RADIUS, 0, -RADIUS * .7, -RADIUS * .7,
            0, -RADIUS,  RADIUS * .7, -RADIUS * .7,
    };

    private double  x, y;
    private double  angle, speed;
    private String  name;
    private Color   color;
    private int     health;
    private double  dist0, dist1, dist2, dist3, dist4;
    private int     counter;
    private int     laps;
    public Bot(double x, double y, double angleDegrees, String name, Color color) {
        this.x = x;
        this.y = y;
        this.name = name;
        this.color  = color;
        this.angle  = angleDegrees * DEG_TO_RAD;
        this.health = START_HEALTH;
    }
    public final void draw(Graphics g, World world) {
        int rad = (int)(World.PX_METER * 2 * RADIUS);
        // Draw black outline:
        g.setColor(Color.BLACK);
        g.fillOval((int)(World.PX_METER * (x - RADIUS) - 2),
                (int)(World.PX_METER * (y - RADIUS) - 2),
                rad + 4, rad + 4);
        // Draw center, with flashy effect at low health:
        if (health > 2
                || (health == 1 && counter % 8 < 4)
                || (health == 2 && counter % 32 < 28)) {
            g.setColor(color);
        } else {
            g.setColor(Color.RED);
        }
        g.fillOval((int)(World.PX_METER * (x - RADIUS)),
                (int)(World.PX_METER * (y - RADIUS)),
                rad, rad);
        g.setColor(Color.WHITE);
        double dx = RADIUS * Math.cos(angle);
        double dy = RADIUS * Math.sin(angle);
        g.drawLine((int)(World.PX_METER * x),
                (int)(World.PX_METER * y),
                (int)(World.PX_METER * (x + dx)),
                (int)(World.PX_METER * (y + dy)));
    }
    public final void drawExtra(Graphics g, World world) {
        g.setColor(Color.WHITE);
        g.setFont(MainGame.bigFont);
        g.drawString(name, 325, 200);
        g.drawString(String.format("angle: %.3f degrees", getAngle()), 325, 230);
        g.drawString(String.format("speed: %.3f m/s", speed),    325, 260);
        g.drawString(String.format("d0 (L90): %.3f ", dist0),   325, 290);
        g.drawString(String.format("d1 (L45): %.3f ", dist1),   325, 320);
        g.drawString(String.format("d2 (0)  : %.3f ", dist2),   325, 350);
        g.drawString(String.format("d3 (R45): %.3f ", dist3),   325, 380);
        g.drawString(String.format("d4 (R90): %.3f ", dist4),   325, 410);
        g.drawString(String.format("counter: %d", counter),   325, 440);
        g.drawString(String.format("quadrant: %d laps: %d", world.getQuadrant(x, y), laps),   325, 470);
        g.drawString(getStatus(), 325, 500);
        drawRay(g, world, -90);
        drawRay(g, world, -45);
        drawRay(g, world, 0);
        drawRay(g, world, 45);
        drawRay(g, world, 90);
    }
    public final int getLaps() {
        return laps;
    }
    public final double getSpeed() {
        return speed;
    }
    public final int getCounter() {
        return counter;
    }
    // Get angle in degrees.
    public final double getAngle() {
        return angle * RAD_TO_DEG;
    }
    public final double getX() {
        return x;
    }
    public final double getY() {
        return y;
    }
    public final String getName() {
        return name;
    }
    public final void generateParticles(ArrayList<Particle> particles, Random rand) {
        for (int i = 0; i < 100; i++) {
            double v  = rand.nextDouble() * 10.0;
            double a  = 2 * Math.PI * rand.nextDouble();
            double vx = v * Math.cos(a);
            double vy = v * Math.sin(a);
            particles.add(new Particle(x, y, vx, vy, 60 + rand.nextInt(60)));
        }
    }
    public final boolean isDead() {
        return health <= 0;
    }
    public final void update(World world) {
        if (health <= 0) {
            return;
        }
        int oldQuadrant = world.getQuadrant(x, y);
        // update stats:
        dist0 = scanRay(world, -90);
        dist1 = scanRay(world, -45);
        dist2 = scanRay(world, 0);
        dist3 = scanRay(world, 45);
        dist4 = scanRay(world, 90);
        counter++;
        int action = getAction(dist0, dist1, dist2, dist3, dist4);
        switch (action) {
            case DRIFT:                                break;
            case ACCEL: compoundAccel(MAX_ACCEL, 0);  break;
            case BRAKE: compoundAccel(-MAX_ACCEL, 0);  break;
            case LEFT:  compoundAccel(0, -MAX_ACCEL);  break;
            case RIGHT: compoundAccel(0,  MAX_ACCEL);  break;
            case ACCEL_RIGHT: compoundAccel(0.7 * MAX_ACCEL, 0.7 * MAX_ACCEL);  break;
            case BRAKE_RIGHT: compoundAccel(-0.7 * MAX_ACCEL, 0.7 * MAX_ACCEL);  break;
            case ACCEL_LEFT:  compoundAccel(0.7 * MAX_ACCEL, -0.7 * MAX_ACCEL);  break;
            case BRAKE_LEFT:  compoundAccel(-0.7 * MAX_ACCEL, -0.7 * MAX_ACCEL);  break;
            case SLIGHT_RIGHT: compoundAccel(0,  0.5 * MAX_ACCEL);  break;
            case SLIGHT_LEFT:  compoundAccel(0, -0.5 * MAX_ACCEL);  break;
        }
        updatePhysics();
        if (checkCrash(world)) {
            angle += Math.PI;  // turn around 180 degrees
            updatePhysics();   // this reverts to previous pre-crash position
            speed *= 0.5;      // friction loss of KE
            health--;
        }

        // The quadrant will be for 'lap stuff': we bump the lap counter when
        // moving from quadrant 3 to 0.
        //   3 | 0
        //  ---+---
        //   2 | 1
        int newQuadrant = world.getQuadrant(x, y);
        if (oldQuadrant == 3 && newQuadrant == 0) {
            laps++;
        } else if (oldQuadrant == 0 && newQuadrant == 3) {
            laps--;
        }
    }
    private final void updatePhysics() {
        x += speed * Math.cos(angle) * DT;
        y += speed * Math.sin(angle) * DT;
    }
    // return distance to world at a relative angle in degrees.
    public final double scanRay(World world, double relativeAngleDegrees) {
        double a = angle + relativeAngleDegrees * DEG_TO_RAD;
        double dx = Math.cos(a) * SCAN_INCREMENT;
        double dy = Math.sin(a) * SCAN_INCREMENT;
        for (int i = 0; SCAN_INCREMENT * i < SCAN_MAX_DIST; i++) {
            if (world.checkWorldPoint(x + i * dx, y + i * dy)) {
                return i * SCAN_INCREMENT;
            }
        }
        return SCAN_MAX_DIST;
    }
    // return distance to world at a relative angle in degrees.
    private final void drawRay(Graphics g,
                               World world,
                               double relativeAngleDegrees) {
        double a = angle + relativeAngleDegrees * DEG_TO_RAD;
        double dx = Math.cos(a) * SCAN_INCREMENT;
        double dy = Math.sin(a) * SCAN_INCREMENT;
        g.setColor(Color.RED);
        for (int i = 0; SCAN_INCREMENT * i < SCAN_MAX_DIST; i++) {
            if (world.checkWorldPoint(x + i * dx, y + i * dy)) {
                return;
            }
            g.fillRect((int)(World.PX_METER * (x + i * dx)),
                    (int)(World.PX_METER * (y + i * dy)), 3, 3);
        }
    }
    private final boolean checkCrash(World world) {
        for (int i = 0; i < INCREMENTS.length; i += 2) {
            if (world.checkWorldPoint(x + INCREMENTS[i], y + INCREMENTS[i + 1])) {
                return true;
            }
        }
        return false;
    }
    private final void compoundAccel(double forwardAccel, double turnAccel) {
        // Requirements:
        // Our 'tires' have limited grip, so limit our total acceleration to MAX_ACCEL:
        //   With fA = forwardAccel, tA = turnAccel, mA = MAX_ACCEL:
        //       fA^2 + tA^2 <= mA^2
        if (forwardAccel * forwardAccel + turnAccel * turnAccel > MAX_ACCEL * MAX_ACCEL) {
            System.out.println("compoundAccel: Accel too high!");
            return;
        }
        // *** Forward acceleration ***
        // Cap forward acceleration based on engine power:
        // KE, which is proportional to speed^2 / 2, should increase steadily...
        // so, taking the derivative, accel * speed should be at most a constant,
        // equal to MAX_POWER.
        //   With s = speed, mP = MAX_POWER (only when fA > 0 is this a concern):
        //       fA * s      <= mP
        if (speed * forwardAccel > MAX_POWER) {
            forwardAccel = MAX_POWER / speed;
        }
        speed += forwardAccel * DT;
        // Braking should not have us go backwards:
        if (speed < 0) {
            speed = 0;
        }
        // *** Turning acceleration ***
        if (speed > 0) {
            // Limit turning radius to R_MIN at the least:
            if (turnAccel > speed * speed / R_MIN) {
                turnAccel = speed * speed / R_MIN;
            } else if (turnAccel < -speed * speed / R_MIN) {
                turnAccel = -speed * speed / R_MIN;
            }
            // speed^2 / r should turnAccel, in other words, r equals
            // speed ^2 * turnAccel, and so the rate of change of the angle is
            // the reciprocal of that.
            double deltaAngle = turnAccel / (speed * speed);
            angle += deltaAngle * DT;
        }
    }
    // *** You will override this method in a subclass. ***
    // This method needs to return DRIFT, ACCEL, BRAKE, LEFT, or RIGHT.
    // dist0, dist1, ... are the distances as measured by the 4 lasers
    // dist2 is straight ahead; dist0 is...the left one?
    public int getAction(double dist0,
                         double dist1,
                         double dist2,
                         double dist3,
                         double dist4) {
        return ACCEL;
    }
    // You should override this to do debugging stuff as needed
    public String getStatus() {
        return "Status: N/a";
    }
}

// Make your own Bot subclass here.  Override getAction only.
// Make the constructor take only double x, double y, and double angleDegrees.
// Make your own Bot subclass here.  Override getAction only.
// Make the constructor take only double x, double y, and double angleDegrees.
class MyBot extends Bot {
    public MyBot(double x, double y, double angleDegrees) {
        super(x, y, angleDegrees, "Doug's Bot", Color.decode("42523"));

//        turnPreferences.put(15, InitiatedTurn.RIGHT);
//        turnPreferences.put(16, InitiatedTurn.RIGHT);
        turnPreferences.put(18, InitiatedTurn.RIGHT);
//        turnPreferences.put(18, InitiatedTurn.RIGHT);
//        turnPreferences.put(19, InitiatedTurn.RIGHT);
    }
    @Override
    public String getStatus() {
        return "State: ??";
    }

    enum InitiatedTurn {
        LEFT,
        RIGHT
    }

    private InitiatedTurn initiatedTurn = InitiatedTurn.RIGHT;

    private int turnCount = 0;
    private boolean turnCountCached = false;

    private HashMap<Integer, InitiatedTurn> turnPreferences = new HashMap<>();

    private int lastLapCount = 0;

    @Override
    // This method must return one of these possible actions:
    //   DRIFT, ACCEL, BRAKE, LEFT, RIGHT, ACCEL_RIGHT, BRAKE_RIGHT,
    //   ACCEL_LEFT, BRAKE_LEFT, SLIGHT_RIGHT, SLIGHT_LEFT
    // The inputs to this method are:
    //   dist0 and dist1 are distance measurements to the left,  90 and 45 degrees, respectively
    //   dist3 and dist4 are distance measurements to the right, 45 and 90 degrees, respectively
    //   dist2 is straight ahead.
    public int getAction(double dist0,
                         double dist1,
                         double dist2,
                         double dist3,
                         double dist4) {
        double speed = getSpeed();

        if(getLaps() != lastLapCount) {
            lastLapCount = getLaps();

            if(lastLapCount <= 1)
                turnCount = 3;
            else if(lastLapCount == 2) {
                turnCount = 3;
            } else {
                turnCount = 3;
            }
        }

        double lrError = (dist1 - dist3) * speed;

        if(dist2 >= 10) {
            turnCountCached = false;

            if (lrError > 0) {
                initiatedTurn = InitiatedTurn.LEFT;
                return ACCEL_LEFT;
            } else if (lrError < 0) {
                initiatedTurn = InitiatedTurn.RIGHT;
                return ACCEL_RIGHT;
            }
        } else {
            System.out.println(turnCount);

            if(!turnCountCached) {
                turnCountCached = true;
                turnCount++;
            }

            if(lrError > 0)
                initiatedTurn = InitiatedTurn.LEFT;
            else if(lrError < 0)
                initiatedTurn = InitiatedTurn.RIGHT;

            if(turnPreferences.containsKey(turnCount)) {
                initiatedTurn = turnPreferences.get(turnCount);
            }

            if(speed > 4.25)
                return initiatedTurn == InitiatedTurn.RIGHT ? BRAKE_RIGHT : BRAKE_LEFT;
            else
                return initiatedTurn == InitiatedTurn.RIGHT ?  ACCEL_RIGHT : ACCEL_LEFT;
        }


        return ACCEL;

    }
}

class MainGame extends GameBase {
    public static final Font bigFont = new Font(Font.MONOSPACED, Font.BOLD, 34);
    public static final int WINDOW_WIDTH = 1200, WINDOW_HEIGHT = 900;
    public static final boolean SHOW_TITLE_BAR = false;

    private ArrayList<Bot>      bots;
    private World               world;
    private int                 selected; // currently selected bot

    private int                 frameCounter;
    private int                 speed;    // if > 1, it's a slowdown factor for debugging stuff
    private Random              rand;
    private ArrayList<Particle> particles;
    private int                 lapsNeeded; // how many laps needed to complete the race
    private boolean             raceIsOn;   // set to false when someone completes the race
    private int                 winner;

    private String              message;
    private int                 messageTimer;
    public MainGame(JFrame window) {
        super(window);
        window.setTitle("Bot Cars Stuff");
        bots = new ArrayList<Bot>();
        world = new World("/World2.png");
        double startX = world.getStartX();
        double startY = world.getStartY();
        bots.add(new Bot(startX, startY, 0.0, "Generic Bot", Color.BLUE));
        bots.add(new MyBot(startX, startY, 0.0));
        selected   = 1;
        speed      = 1;
        rand       = new Random(17);

        particles  = new ArrayList<Particle>();
        raceIsOn   = true;
        lapsNeeded = 3;
        winner     = -1;
    }

    public void update() {
        if (isKeyDown(VK_ESCAPE)) {
            quit();
        }

        if (wasKeyPressed(VK_DOWN)) {
            speed++;
        }
        if (wasKeyPressed(VK_UP) && speed > 1) {
            speed--;
        }
        if (wasKeyPressed(VK_PAGE_DOWN) && selected > -1) {
            selected--;
        }
        if (wasKeyPressed(VK_PAGE_UP) && selected + 1 < bots.size()) {
            selected++;
        }
        if (messageTimer > 0) {
            messageTimer--;
        }
        if (raceIsOn) {
            frameCounter++;
        }
        if (raceIsOn && frameCounter % speed == 0) {
            // Update all the bots, remove dead ones
            // Also see who's in the lead
            int leadBot = -1;
            int maxLaps = -1;
            int i = 0;
            while (i < bots.size()) {
                Bot b = bots.get(i);
                b.update(world);
                if (b.getLaps() > maxLaps) {
                    leadBot = i;
                    maxLaps = b.getLaps();
                }
                if (b.isDead()) {
                    b.generateParticles(particles, rand);
                    message = String.format("%s has died!", b.getName());
                    messageTimer = 120;
                    bots.remove(i);
                    if (selected >= i) {
                        selected--;
                    }
                } else {
                    i++;
                }
            }
            i = 0;
            while (i < particles.size()) {
                Particle p = particles.get(i);
                p.update();
                if (p.isExpired()) {
                    particles.remove(i);
                } else {
                    i++;
                }
            }
            if (maxLaps >= lapsNeeded) {
                raceIsOn = false;
                winner   = leadBot;
                message  = String.format("%s wins!", bots.get(winner).getName());
                messageTimer = 600;
            }
        }

    }

    // here's another method:  it's where are the 'drawing' gets done
    public void paintComponent(Graphics g) {
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        world.draw(g);

        // For debugging your bot:
        if (selected >= 0 && selected < bots.size()) {
            bots.get(selected).drawExtra(g, world);
        }
        for (Bot b : bots) {
            b.draw(g, world);
        }
        // Press up/down to slow down the simulation.
        if (speed > 1) {
            g.setFont(bigFont);
            g.setColor(Color.RED);
            g.drawString("1/" + speed + " speed", 20, 50);
        }
        for (Particle p : particles) {
            p.draw(g);
        }
        g.setColor(Color.WHITE);
        g.setFont(bigFont);
        g.drawString(String.format("%.2f", frameCounter / 60.0), 1050, 50);
        if (messageTimer > 0) {
            g.drawString(message, 450, 50);
        }
    }
}

