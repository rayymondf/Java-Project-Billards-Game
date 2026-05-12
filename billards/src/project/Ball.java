package project;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;

class Ball {
    final BallGroup group;
    final Color color;
    final double radius;

    double x;
    double y;
    double vx;
    double vy;
    boolean pocketed;

    Ball(BallGroup group, Color color, double x, double y, double radius) {
        this.group = group;
        this.color = color;
        this.x = x;
        this.y = y;
        this.radius = radius;
    }

    double speedSquared() {
        return vx * vx + vy * vy;
    }

    boolean isMoving(double threshold) {
        return speedSquared() > threshold * threshold;
    }

    void stop() {
        vx = 0;
        vy = 0;
    }

    void draw(Graphics2D g2d) {
        if (pocketed) {
            return;
        }

        int diameter = (int) Math.round(radius * 2);
        int left = (int) Math.round(x - radius);
        int top = (int) Math.round(y - radius);

        g2d.setColor(group == BallGroup.STRIPE || group == BallGroup.CUE ? Color.WHITE : color);
        g2d.fillOval(left, top, diameter, diameter);

        if (group == BallGroup.STRIPE) {
            Shape oldClip = g2d.getClip();
            g2d.setClip(new Ellipse2D.Double(left, top, diameter, diameter));
            g2d.setColor(color);
            g2d.fillRect(left, top + diameter / 5, diameter, diameter * 3 / 5);
            g2d.setClip(oldClip);
        }

        g2d.setColor(new Color(20, 20, 20));
        g2d.drawOval(left, top, diameter, diameter);
    }
}
