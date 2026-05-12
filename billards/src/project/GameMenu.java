package project;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

@SuppressWarnings({ "serial", "this-escape" })
public class GameMenu extends JPanel implements ActionListener {
    private static final long serialVersionUID = 1L;
    private static final int WIDTH = 960;
    private static final int HEIGHT = 640;

    private final JButton startButton = new JButton("START");
    private final JButton rulesButton = new JButton("RULES");
    private final JTextArea rulesText = new JTextArea();

    public GameMenu() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(34, 96, 63));
        setLayout(null);

        JLabel title = new JLabel("8-Ball Pool");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 42));
        title.setBounds(355, 65, 280, 60);

        startButton.setBounds(325, 170, 140, 48);
        rulesButton.setBounds(495, 170, 140, 48);
        startButton.addActionListener(this);
        rulesButton.addActionListener(this);

        rulesText.setBounds(160, 250, 640, 260);
        rulesText.setEditable(false);
        rulesText.setVisible(false);
        rulesText.setLineWrap(true);
        rulesText.setWrapStyleWord(true);
        rulesText.setMargin(new Insets(12, 12, 12, 12));
        rulesText.setFont(new Font("SansSerif", Font.PLAIN, 16));
        rulesText.setForeground(Color.BLACK);
        rulesText.setBackground(Color.WHITE);
        rulesText.setText("RULES\n\n"
                + "1. The table starts open. The first legal solid or stripe pocketed chooses the groups.\n"
                + "2. Solids are full red balls. Stripes are white balls with a blue band. The black 8-ball is saved for last.\n"
                + "3. Pocket all balls in your group before trying to pocket the 8-ball.\n"
                + "4. Fouls: scratching the cue ball, missing every ball, hitting the wrong ball first, "
                + "or failing to hit a rail/pocket a ball after contact.\n"
                + "5. After a foul, the other player gets ball in hand.\n\n"
                + "Controls: move the mouse or left/right arrows to aim, up/down to set power, "
                + "space/enter or mouse drag/release to shoot.");

        add(title);
        add(startButton);
        add(rulesButton);
        add(rulesText);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (event.getSource() == rulesButton) {
            rulesText.setVisible(!rulesText.isVisible());
        } else if (event.getSource() == startButton) {
            GamePanel gamePanel = new GamePanel();
            Main.centerPanel.add(gamePanel, "game");
            Main.navigation.show(Main.centerPanel, "game");
            gamePanel.requestFocusInWindow();
        }
    }
}
