package project;

import java.awt.CardLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class Main {
    public static final CardLayout navigation = new CardLayout();
    public static final JFrame frame = new JFrame("8-Ball Pool");
    public static final JPanel centerPanel = new JPanel(navigation);

    public Main() {
        centerPanel.add(new GameMenu(), "menu");

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setContentPane(centerPanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::new);
    }
}
