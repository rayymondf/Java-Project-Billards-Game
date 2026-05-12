package project;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.Timer;

@SuppressWarnings({ "serial", "this-escape" })
public class GamePanel extends JPanel implements ActionListener, KeyListener, MouseListener, MouseMotionListener {
    private static final long serialVersionUID = 1L;
    private static final int PANEL_WIDTH = 960;
    private static final int PANEL_HEIGHT = 640;
    private static final int TABLE_X = 80;
    private static final int TABLE_Y = 130;
    private static final int TABLE_WIDTH = 800;
    private static final int TABLE_HEIGHT = 360;
    private static final double BALL_RADIUS = 13.0;
    private static final double POCKET_RADIUS = 30.0;
    private static final double FRICTION = 0.985;
    private static final double CUSHION_BOUNCE = 0.82;
    private static final double COLLISION_RESTITUTION = 0.96;
    private static final double STOP_SPEED = 0.055;
    private static final double MIN_POWER = 5.0;
    private static final double MAX_POWER = 24.0;
    private static final double POWER_STEP = 1.0;
    private static final double AIM_STEP = Math.toRadians(4.0);

    private final Timer timer = new Timer(16, this);
    private final List<Ball> objectBalls = new ArrayList<>();
    private final List<Point2D.Double> pockets = new ArrayList<>();
    private final Player[] players = { new Player("Player 1"), new Player("Player 2") };

    private Ball cueBall;
    private int currentPlayer;
    private double aimAngle;
    private double shotPower;
    private boolean canShoot;
    private boolean shotInProgress;
    private boolean ballInHand;
    private boolean gameOver;
    private boolean draggingShot;
    private String statusText;
    private ShotStats shotStats;
    private String lastFoul;

    public GamePanel() {
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(new Color(35, 35, 35));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        createPockets();
        resetGame();
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawTable(g2d);
        drawAimingTools(g2d);

        for (Ball ball : objectBalls) {
            ball.draw(g2d);
        }
        cueBall.draw(g2d);
        drawBallInHandMarker(g2d);
        drawHud(g2d);

        if (gameOver) {
            drawGameOver(g2d);
        }

        g2d.dispose();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (!gameOver && shotInProgress) {
            simulateFrame();
            if (allBallsStopped()) {
                resolveShot();
            }
        }
        repaint();
    }

    private void resetGame() {
        objectBalls.clear();
        players[0].group = BallGroup.UNASSIGNED;
        players[1].group = BallGroup.UNASSIGNED;
        currentPlayer = 0;
        aimAngle = 0;
        shotPower = 14.0;
        canShoot = true;
        shotInProgress = false;
        ballInHand = false;
        gameOver = false;
        draggingShot = false;
        shotStats = null;
        lastFoul = "";
        statusText = "Break shot. Table is open.";

        cueBall = new Ball(BallGroup.CUE, Color.WHITE, TABLE_X + 185, tableCenterY(), BALL_RADIUS);
        rackBalls();
    }

    private void rackBalls() {
        double rackX = TABLE_X + TABLE_WIDTH * 0.70;
        double rackY = tableCenterY();
        double spacingX = BALL_RADIUS * 1.74;
        double spacingY = BALL_RADIUS * 2.04;
        int[] rackOrder = { 1, 9, 2, 10, 8, 3, 11, 4, 12, 5, 13, 6, 14, 7, 15 };
        int index = 0;

        for (int col = 0; col < 5; col++) {
            double x = rackX + col * spacingX;
            double firstY = rackY - col * spacingY / 2.0;
            for (int row = 0; row <= col; row++) {
                int number = rackOrder[index++];
                objectBalls.add(createObjectBall(number, x, firstY + row * spacingY));
            }
        }
    }

    private Ball createObjectBall(int number, double x, double y) {
        BallGroup group = number == 8 ? BallGroup.EIGHT : number < 8 ? BallGroup.SOLID : BallGroup.STRIPE;
        Color color = group == BallGroup.STRIPE ? new Color(45, 96, 210) : new Color(210, 45, 45);
        if (group == BallGroup.EIGHT) {
            color = Color.BLACK;
        }
        return new Ball(group, color, x, y, BALL_RADIUS);
    }

    private void createPockets() {
        pockets.clear();
        pockets.add(new Point2D.Double(TABLE_X, TABLE_Y));
        pockets.add(new Point2D.Double(TABLE_X + TABLE_WIDTH / 2.0, TABLE_Y));
        pockets.add(new Point2D.Double(TABLE_X + TABLE_WIDTH, TABLE_Y));
        pockets.add(new Point2D.Double(TABLE_X, TABLE_Y + TABLE_HEIGHT));
        pockets.add(new Point2D.Double(TABLE_X + TABLE_WIDTH / 2.0, TABLE_Y + TABLE_HEIGHT));
        pockets.add(new Point2D.Double(TABLE_X + TABLE_WIDTH, TABLE_Y + TABLE_HEIGHT));
    }

    private void simulateFrame() {
        int subSteps = Math.max(1, (int) Math.ceil(maxBallSpeed() / (BALL_RADIUS * 0.7)));
        double dt = 1.0 / subSteps;
        for (int i = 0; i < subSteps; i++) {
            updateBall(cueBall, dt);
            for (Ball ball : objectBalls) {
                updateBall(ball, dt);
            }
            checkCollisions();
            handlePocketedBalls();
        }
    }

    private void updateBall(Ball ball, double dt) {
        if (ball.pocketed) {
            return;
        }

        ball.x += ball.vx * dt;
        ball.y += ball.vy * dt;

        double frameFriction = Math.pow(FRICTION, dt);
        ball.vx *= frameFriction;
        ball.vy *= frameFriction;

        if (shotStats != null && isInPocket(ball)) {
            return;
        }

        boolean railHit = false;
        double left = TABLE_X + BALL_RADIUS;
        double right = TABLE_X + TABLE_WIDTH - BALL_RADIUS;
        double top = TABLE_Y + BALL_RADIUS;
        double bottom = TABLE_Y + TABLE_HEIGHT - BALL_RADIUS;

        if (ball.x < left) {
            ball.x = left;
            ball.vx = Math.abs(ball.vx) * CUSHION_BOUNCE;
            railHit = true;
        } else if (ball.x > right) {
            ball.x = right;
            ball.vx = -Math.abs(ball.vx) * CUSHION_BOUNCE;
            railHit = true;
        }

        if (ball.y < top) {
            ball.y = top;
            ball.vy = Math.abs(ball.vy) * CUSHION_BOUNCE;
            railHit = true;
        } else if (ball.y > bottom) {
            ball.y = bottom;
            ball.vy = -Math.abs(ball.vy) * CUSHION_BOUNCE;
            railHit = true;
        }

        if (railHit && shotStats != null && shotStats.firstHit != null) {
            shotStats.railAfterContact = true;
        }

        if (!ball.isMoving(STOP_SPEED)) {
            ball.stop();
        }
    }

    private void checkCollisions() {
        for (Ball ball : objectBalls) {
            collide(cueBall, ball);
        }

        for (int i = 0; i < objectBalls.size(); i++) {
            for (int j = i + 1; j < objectBalls.size(); j++) {
                collide(objectBalls.get(i), objectBalls.get(j));
            }
        }
    }

    private void collide(Ball first, Ball second) {
        if (first.pocketed || second.pocketed) {
            return;
        }

        double dx = second.x - first.x;
        double dy = second.y - first.y;
        double distance = Math.hypot(dx, dy);
        double minDistance = first.radius + second.radius;

        if (distance >= minDistance) {
            return;
        }

        if (shotStats != null && shotStats.firstHit == null) {
            if (first == cueBall && second.group != BallGroup.CUE) {
                shotStats.firstHit = second;
            } else if (second == cueBall && first.group != BallGroup.CUE) {
                shotStats.firstHit = first;
            }
        }

        double nx = 1.0;
        double ny = 0.0;
        if (distance > 0.0001) {
            nx = dx / distance;
            ny = dy / distance;
        }
        double overlap = minDistance - distance;
        first.x -= nx * overlap / 2.0;
        first.y -= ny * overlap / 2.0;
        second.x += nx * overlap / 2.0;
        second.y += ny * overlap / 2.0;

        double relativeVx = second.vx - first.vx;
        double relativeVy = second.vy - first.vy;
        double closingSpeed = relativeVx * nx + relativeVy * ny;
        if (closingSpeed > 0) {
            return;
        }

        double impulse = -(1 + COLLISION_RESTITUTION) * closingSpeed / 2.0;
        first.vx -= impulse * nx;
        first.vy -= impulse * ny;
        second.vx += impulse * nx;
        second.vy += impulse * ny;
    }

    private void handlePocketedBalls() {
        if (shotStats == null) {
            return;
        }

        if (!cueBall.pocketed && isInPocket(cueBall)) {
            cueBall.pocketed = true;
            cueBall.stop();
            shotStats.cuePocketed = true;
        }

        Iterator<Ball> iterator = objectBalls.iterator();
        while (iterator.hasNext()) {
            Ball ball = iterator.next();
            if (isInPocket(ball)) {
                ball.pocketed = true;
                ball.stop();
                shotStats.pocketed.add(ball);
                iterator.remove();
            }
        }
    }

    private void resolveShot() {
        shotInProgress = false;
        stopAllBalls();

        boolean foul = isFoul();
        boolean eightPocketed = shotStats.pocketedEight();

        if (eightPocketed) {
            resolveEightBall(foul);
            shotStats = null;
            return;
        }

        if (!foul && players[currentPlayer].group == BallGroup.UNASSIGNED) {
            assignGroupsFromShot();
        }

        boolean shooterKeepsTurn = !foul && shotStats.pocketedGroup(players[currentPlayer].group);
        if (foul) {
            currentPlayer = opponentIndex();
            enterBallInHand(lastFoul + " " + players[currentPlayer].name + " has ball in hand.");
        } else if (shooterKeepsTurn) {
            canShoot = true;
            statusText = players[currentPlayer].name + " made a ball and shoots again.";
        } else {
            currentPlayer = opponentIndex();
            canShoot = true;
            statusText = players[currentPlayer].name + "'s turn.";
        }

        shotStats = null;
    }

    private boolean isFoul() {
        if (shotStats.cuePocketed) {
            lastFoul = "Scratch.";
            return true;
        }

        if (shotStats.firstHit == null) {
            lastFoul = "Foul: cue ball missed every object ball.";
            return true;
        }

        if (!isLegalFirstHit(shotStats.firstHit)) {
            lastFoul = "Foul: first contact was the wrong ball.";
            return true;
        }

        if (shotStats.pocketed.isEmpty() && !shotStats.railAfterContact) {
            lastFoul = "Foul: no ball was pocketed and no rail was hit after contact.";
            return true;
        }

        lastFoul = "";
        return false;
    }

    private boolean isLegalFirstHit(Ball firstHit) {
        Player player = players[currentPlayer];
        if (player.group == BallGroup.UNASSIGNED) {
            return firstHit.group == BallGroup.SOLID || firstHit.group == BallGroup.STRIPE;
        }
        if (remainingBalls(player.group) == 0) {
            return firstHit.group == BallGroup.EIGHT;
        }
        return firstHit.group == player.group;
    }

    private void resolveEightBall(boolean foul) {
        Player shooter = players[currentPlayer];
        boolean legalEight = !foul
                && shooter.group != BallGroup.UNASSIGNED
                && remainingBalls(shooter.group) == 0
                && shotStats.firstHit != null
                && shotStats.firstHit.group == BallGroup.EIGHT;

        gameOver = true;
        canShoot = false;
        ballInHand = false;
        if (legalEight) {
            statusText = shooter.name + " wins by legally pocketing the 8-ball.";
        } else {
            statusText = players[opponentIndex()].name + " wins. The 8-ball was pocketed illegally.";
        }
    }

    private void assignGroupsFromShot() {
        for (Ball ball : shotStats.pocketed) {
            if (ball.group == BallGroup.SOLID || ball.group == BallGroup.STRIPE) {
                players[currentPlayer].group = ball.group;
                players[opponentIndex()].group = ball.group.opposite();
                statusText = players[currentPlayer].name + " is " + ball.group.label() + ".";
                return;
            }
        }
    }

    private void shoot() {
        if (!canShoot || ballInHand || gameOver || !allBallsStopped()) {
            return;
        }
        shotStats = new ShotStats();
        cueBall.pocketed = false;
        cueBall.vx = shotPower * Math.cos(aimAngle);
        cueBall.vy = shotPower * Math.sin(aimAngle);
        canShoot = false;
        shotInProgress = true;
        statusText = players[currentPlayer].name + " shot.";
    }

    private void enterBallInHand(String message) {
        ballInHand = true;
        canShoot = false;
        cueBall.pocketed = false;
        cueBall.stop();
        moveCueToNearestOpenSpot(TABLE_X + TABLE_WIDTH * 0.25, tableCenterY());
        statusText = message;
    }

    private void placeCueFromHand() {
        if (!ballInHand) {
            return;
        }
        if (validCuePlacement(cueBall.x, cueBall.y)) {
            ballInHand = false;
            canShoot = true;
            statusText = players[currentPlayer].name + " is shooting.";
        } else {
            statusText = "Ball in hand: choose an open spot on the table.";
        }
    }

    private void moveCueToNearestOpenSpot(double x, double y) {
        moveCueTo(x, y);
        if (validCuePlacement(cueBall.x, cueBall.y)) {
            return;
        }

        double step = BALL_RADIUS * 2.2;
        for (double scanY = TABLE_Y + BALL_RADIUS; scanY <= TABLE_Y + TABLE_HEIGHT - BALL_RADIUS; scanY += step) {
            for (double scanX = TABLE_X + BALL_RADIUS; scanX <= TABLE_X + TABLE_WIDTH - BALL_RADIUS; scanX += step) {
                if (validCuePlacement(scanX, scanY)) {
                    cueBall.x = scanX;
                    cueBall.y = scanY;
                    return;
                }
            }
        }
    }

    private void moveCueTo(double x, double y) {
        cueBall.x = clamp(x, TABLE_X + BALL_RADIUS, TABLE_X + TABLE_WIDTH - BALL_RADIUS);
        cueBall.y = clamp(y, TABLE_Y + BALL_RADIUS, TABLE_Y + TABLE_HEIGHT - BALL_RADIUS);
    }

    private boolean validCuePlacement(double x, double y) {
        if (x < TABLE_X + BALL_RADIUS || x > TABLE_X + TABLE_WIDTH - BALL_RADIUS
                || y < TABLE_Y + BALL_RADIUS || y > TABLE_Y + TABLE_HEIGHT - BALL_RADIUS) {
            return false;
        }

        for (Ball ball : objectBalls) {
            if (distance(x, y, ball.x, ball.y) < BALL_RADIUS * 2.15) {
                return false;
            }
        }
        return true;
    }

    private boolean isInPocket(Ball ball) {
        for (Point2D.Double pocket : pockets) {
            if (distance(ball.x, ball.y, pocket.x, pocket.y) < POCKET_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private boolean allBallsStopped() {
        if (!cueBall.pocketed && cueBall.isMoving(STOP_SPEED)) {
            return false;
        }
        for (Ball ball : objectBalls) {
            if (ball.isMoving(STOP_SPEED)) {
                return false;
            }
        }
        return true;
    }

    private void stopAllBalls() {
        cueBall.stop();
        for (Ball ball : objectBalls) {
            ball.stop();
        }
    }

    private int remainingBalls(BallGroup group) {
        int count = 0;
        for (Ball ball : objectBalls) {
            if (ball.group == group) {
                count++;
            }
        }
        return count;
    }

    private int opponentIndex() {
        return 1 - currentPlayer;
    }

    private double maxBallSpeed() {
        double max = Math.sqrt(cueBall.speedSquared());
        for (Ball ball : objectBalls) {
            max = Math.max(max, Math.sqrt(ball.speedSquared()));
        }
        return max;
    }

    private double tableCenterY() {
        return TABLE_Y + TABLE_HEIGHT / 2.0;
    }

    private double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateAimFrom(Point point) {
        aimAngle = Math.atan2(point.y - cueBall.y, point.x - cueBall.x);
    }

    private void adjustCueForMouse(Point point) {
        if (ballInHand) {
            moveCueTo(point.x, point.y);
        } else if (canShoot) {
            updateAimFrom(point);
        }
    }

    private void drawTable(Graphics2D g2d) {
        g2d.setColor(new Color(95, 55, 25));
        g2d.fillRect(TABLE_X - 28, TABLE_Y - 28, TABLE_WIDTH + 56, TABLE_HEIGHT + 56);

        g2d.setColor(new Color(24, 130, 78));
        g2d.fillRect(TABLE_X, TABLE_Y, TABLE_WIDTH, TABLE_HEIGHT);

        g2d.setColor(Color.BLACK);
        for (Point2D.Double pocket : pockets) {
            int left = (int) Math.round(pocket.x - POCKET_RADIUS);
            int top = (int) Math.round(pocket.y - POCKET_RADIUS);
            int diameter = (int) Math.round(POCKET_RADIUS * 2);
            g2d.fillOval(left, top, diameter, diameter);
        }
    }

    private void drawAimingTools(Graphics2D g2d) {
        if (!canShoot || ballInHand || gameOver || shotInProgress) {
            return;
        }

        double endX = cueBall.x + Math.cos(aimAngle) * 210;
        double endY = cueBall.y + Math.sin(aimAngle) * 210;

        g2d.setStroke(new BasicStroke(2f));
        g2d.setColor(Color.WHITE);
        g2d.draw(new Line2D.Double(cueBall.x, cueBall.y, endX, endY));
    }

    private void drawBallInHandMarker(Graphics2D g2d) {
        if (!ballInHand) {
            return;
        }
        g2d.setStroke(new BasicStroke(2f));
        g2d.setColor(validCuePlacement(cueBall.x, cueBall.y) ? new Color(255, 255, 255, 180) : new Color(220, 40, 40));
        int diameter = (int) Math.round(BALL_RADIUS * 2.7);
        g2d.drawOval((int) Math.round(cueBall.x - diameter / 2.0),
                (int) Math.round(cueBall.y - diameter / 2.0), diameter, diameter);
    }

    private void drawHud(Graphics2D g2d) {
        g2d.setColor(new Color(25, 25, 25));
        g2d.fillRect(0, 0, PANEL_WIDTH, 108);
        g2d.fillRect(0, PANEL_HEIGHT - 70, PANEL_WIDTH, 70);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2d.drawString(players[currentPlayer].name + " turn", 36, 34);

        g2d.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g2d.drawString(playerLine(0), 36, 59);
        g2d.drawString(playerLine(1), 36, 80);
        g2d.drawString("Solids: full red balls    Stripes: white balls with blue band    Black 8-ball: last", 305, 80);

        int powerX = PANEL_WIDTH - 230;
        int powerY = 33;
        g2d.setColor(Color.WHITE);
        g2d.drawString("Power", powerX, powerY - 9);
        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRect(powerX, powerY, 170, 14);
        int fill = (int) Math.round((shotPower - MIN_POWER) / (MAX_POWER - MIN_POWER) * 170);
        g2d.setColor(Color.YELLOW);
        g2d.fillRect(powerX, powerY, fill, 14);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(powerX, powerY, 170, 14);

        g2d.setFont(new Font("SansSerif", Font.BOLD, 15));
        drawCenteredString(g2d, statusText, PANEL_WIDTH / 2, PANEL_HEIGHT - 38);
    }

    private String playerLine(int index) {
        Player player = players[index];
        int remaining = player.group == BallGroup.UNASSIGNED ? 0 : remainingBalls(player.group);
        String count = player.group == BallGroup.UNASSIGNED ? "" : " | left: " + remaining;
        return player.name + ": " + player.groupLabel() + count;
    }

    private void drawGameOver(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 155));
        g2d.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        g2d.setColor(new Color(248, 244, 224));
        g2d.setFont(new Font("SansSerif", Font.BOLD, 28));
        drawCenteredString(g2d, statusText, PANEL_WIDTH / 2, PANEL_HEIGHT / 2 - 12);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 16));
        drawCenteredString(g2d, "Press R to rack again", PANEL_WIDTH / 2, PANEL_HEIGHT / 2 + 24);
    }

    private void drawCenteredString(Graphics2D g2d, String text, int centerX, int baselineY) {
        FontMetrics metrics = g2d.getFontMetrics();
        g2d.drawString(text, centerX - metrics.stringWidth(text) / 2, baselineY);
    }

    @Override
    public void keyPressed(KeyEvent event) {
        int key = event.getKeyCode();

        if (key == KeyEvent.VK_R) {
            resetGame();
            return;
        }

        if (gameOver) {
            return;
        }

        if (ballInHand) {
            handleBallInHandKey(key);
            return;
        }

        if (!canShoot) {
            return;
        }

        if (key == KeyEvent.VK_LEFT) {
            aimAngle -= AIM_STEP;
        } else if (key == KeyEvent.VK_RIGHT) {
            aimAngle += AIM_STEP;
        } else if (key == KeyEvent.VK_UP) {
            shotPower = clamp(shotPower + POWER_STEP, MIN_POWER, MAX_POWER);
        } else if (key == KeyEvent.VK_DOWN) {
            shotPower = clamp(shotPower - POWER_STEP, MIN_POWER, MAX_POWER);
        } else if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
            shoot();
        }
    }

    private void handleBallInHandKey(int key) {
        double move = 7.0;
        if (key == KeyEvent.VK_LEFT) {
            cueBall.x = clamp(cueBall.x - move, TABLE_X + BALL_RADIUS, TABLE_X + TABLE_WIDTH - BALL_RADIUS);
        } else if (key == KeyEvent.VK_RIGHT) {
            cueBall.x = clamp(cueBall.x + move, TABLE_X + BALL_RADIUS, TABLE_X + TABLE_WIDTH - BALL_RADIUS);
        } else if (key == KeyEvent.VK_UP) {
            cueBall.y = clamp(cueBall.y - move, TABLE_Y + BALL_RADIUS, TABLE_Y + TABLE_HEIGHT - BALL_RADIUS);
        } else if (key == KeyEvent.VK_DOWN) {
            cueBall.y = clamp(cueBall.y + move, TABLE_Y + BALL_RADIUS, TABLE_Y + TABLE_HEIGHT - BALL_RADIUS);
        } else if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE) {
            placeCueFromHand();
        }
    }

    @Override
    public void mouseMoved(MouseEvent event) {
        adjustCueForMouse(event.getPoint());
    }

    @Override
    public void mouseDragged(MouseEvent event) {
        if (ballInHand) {
            adjustCueForMouse(event.getPoint());
            return;
        }

        if (canShoot) {
            draggingShot = true;
            updateAimFrom(event.getPoint());
            shotPower = clamp(distance(event.getX(), event.getY(), cueBall.x, cueBall.y) / 12.0, MIN_POWER, MAX_POWER);
        }
    }

    @Override
    public void mousePressed(MouseEvent event) {
        requestFocusInWindow();
        if (ballInHand) {
            adjustCueForMouse(event.getPoint());
            return;
        }
        if (canShoot) {
            draggingShot = true;
            updateAimFrom(event.getPoint());
        }
    }

    @Override
    public void mouseReleased(MouseEvent event) {
        if (ballInHand) {
            placeCueFromHand();
            return;
        }
        if (draggingShot && canShoot) {
            updateAimFrom(event.getPoint());
            shoot();
        }
        draggingShot = false;
    }

    @Override
    public void mouseClicked(MouseEvent event) {
    }

    @Override
    public void mouseEntered(MouseEvent event) {
        requestFocusInWindow();
    }

    @Override
    public void mouseExited(MouseEvent event) {
        draggingShot = false;
    }

    @Override
    public void keyTyped(KeyEvent event) {
    }

    @Override
    public void keyReleased(KeyEvent event) {
    }

    private static class ShotStats {
        final List<Ball> pocketed = new ArrayList<>();
        Ball firstHit;
        boolean cuePocketed;
        boolean railAfterContact;

        boolean pocketedEight() {
            for (Ball ball : pocketed) {
                if (ball.group == BallGroup.EIGHT) {
                    return true;
                }
            }
            return false;
        }

        boolean pocketedGroup(BallGroup group) {
            if (group == BallGroup.UNASSIGNED) {
                for (Ball ball : pocketed) {
                    if (ball.group == BallGroup.SOLID || ball.group == BallGroup.STRIPE) {
                        return true;
                    }
                }
                return false;
            }

            for (Ball ball : pocketed) {
                if (ball.group == group) {
                    return true;
                }
            }
            return false;
        }
    }
}
