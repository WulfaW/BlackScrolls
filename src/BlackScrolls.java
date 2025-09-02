// Copyright (c) 2025 Wulfa. All rights reserved.
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.*;

public class BlackScrolls {
    // Animation helper classes
    private enum Destination { PLAYER, DEALER_VISIBLE, DEALER_HIDDEN, LEFT_AI, RIGHT_AI }

    private class DealOrder {
        Card card;
        Destination destination;
        int handIndex;
        String soundFile;

        DealOrder(Card card, Destination destination, int handIndex, String soundFile) {
            this.card = card;
            this.destination = destination;
            this.handIndex = handIndex;
            this.soundFile = soundFile;
        }
    }

    private class AnimatedCard {
        Card card;
        double x, y;
        double targetX, targetY;
        double startX, startY;
        double progress = 0.0;
        double speed = 0.05; // Controls animation speed
        Destination destination;
        int handIndex;

        AnimatedCard(Card card, double startX, double startY, double targetX, double targetY, Destination dest, int handIdx) {
            this.card = card;
            this.x = startX;
            this.y = startY;
            this.startX = startX;
            this.startY = startY;
            this.targetX = targetX;
            this.targetY = targetY;
            this.destination = dest;
            this.handIndex = handIdx;
        }

        void update() {
            progress = Math.min(1.0, progress + speed);
            x = startX + (targetX - startX) * progress;
            y = startY + (targetY - startY) * progress;
        }

        boolean isFinished() {
            return progress >= 1.0;
        }
    }

    private class Card {
        String value;
        String suit;

        Card(String value, String suit) {
            this.value = value;
            this.suit = suit;
        }

        public String toString() {
            return value + "-" + suit;
        }

        public int getValue() {
            switch (value) {
                case "A":
                    return 11;
                case "J":
                case "Q":
                case "K":
                    return 10;
                default:
                    return Integer.parseInt(value);
            }
        }

        public boolean isAce() {
            return value.equals("A");
        }

        public String getImagePath() {
            return "/cards/" + toString() + ".png";
        }
    }

    // Game State
    private enum GameState { PLAYER_TURN, LEFT_AI_TURN, RIGHT_AI_TURN, DEALER_TURN, PAYOUT }
    private GameState gameState;
    private enum HandOutcome { WIN, LOSE, PUSH }

    // Animation
    private ArrayList<AnimatedCard> animatedCards = new ArrayList<>();
    private ArrayList<DealOrder> dealQueue = new ArrayList<>();
    private Timer animationTimer;
    private boolean isSoundtrackMuted = false;
    private Clip soundtrackClip;
    private long lastDealTime = 0;
    private final int DEAL_DELAY = 200; // milliseconds between dealing each card    

    // Cards
    ArrayList<Card> deck;
    Random random = new Random();

    // Dealer
    Card hiddenCard;
    ArrayList<Card> dealerHand;
    int dealerSum;
    int dealerAce;

    // Player
    ArrayList<ArrayList<Card>> playerHand;
    ArrayList<Integer> playerSum;
    ArrayList<Integer> playerAce;
    int activeHandIndex;

    // AI Players
    ArrayList<Card> leftAI;
    ArrayList<Card> rightAI;
    int leftAISum, leftAIAce;
    int rightAISum, rightAIAce;
    int leftAIBalance, rightAIBalance;
    int leftAICurrentBet, rightAICurrentBet;

    // Betting
    int playerBalance;
    ArrayList<Integer> playerBets;
    boolean isGameOver = false;
    ArrayList<String> handResults;

    // Card size
    int cardWidth = 110;
    int cardHeight = 154;

    // Localization
    private String currentLanguage = "en";
    private Map<String, Map<String, String>> translations;
    private JButton trButton;
    private JButton enButton;
    private JButton muteButton;
    private ImageIcon speakerIcon, mutedIcon;

    Image backgroundImage;
    JFrame frame = new JFrame("BlackScrolls");
    JButton tutorialButton;
    JPanel gamePanel = new JPanel() {
        @Override
        public void paintComponent(Graphics g) {
        super.paintComponent(g); // Clears the panel
        if (backgroundImage != null) {
            // Draw the background image, scaled to fit the panel
            g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
        } else {
            // Fallback to a solid color if the image isn't loaded
            g.setColor(new Color(20, 25, 35));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        Graphics2D g2d = (Graphics2D) g.create();
        try {
        int w = getWidth();
        int h = getHeight();

        // Calculate scale based on window size
        double scale = Math.min(w / 1200.0, h / 800.0);
        int cw = (int) (cardWidth * scale);
        int ch = (int) (cardHeight * scale);
        int spacing = Math.max(5, (int) (10 * scale));

        // Enable antialiasing for smoother rendering
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dealer's Cards
        int dealerY = spacing;
        int totalDealerCards = (hiddenCard != null ? 1 : 0) + dealerHand.size();
        int totalDealerWidth = totalDealerCards > 0 ? (totalDealerCards * (cw + spacing) - spacing) : 0;
        int dealerStartX = (w - totalDealerWidth) / 2;
        int currentDealerX = dealerStartX;

        // Highlight for Dealer's turn
        if (gameState == GameState.DEALER_TURN) {
            g2d.setColor(new Color(200, 50, 50, 80)); // Reddish highlight
            g2d.fillRoundRect(dealerStartX - spacing, dealerY - spacing, totalDealerWidth + 2 * spacing, ch + 2 * spacing, 20, 20);
        }

        // Draw hidden card (or revealed card)
        if (hiddenCard != null) {
            // Show back of card if it's player's turn, otherwise show face
            String imagePath = standButton.isEnabled() ? "/cards/BACK.png" : hiddenCard.getImagePath();
            Image cardImage = new ImageIcon(getClass().getResource(imagePath)).getImage();
            g2d.drawImage(cardImage, currentDealerX, dealerY, cw, ch, null);
            currentDealerX += cw + spacing;
        }

        // Draw the rest of the dealer's hand
        for (Card c : dealerHand) {
            Image cardImage = new ImageIcon(getClass().getResource(c.getImagePath())).getImage();
            g2d.drawImage(cardImage, currentDealerX, dealerY, cw, ch, null);
            currentDealerX += cw + spacing;
        }

        // Left AI Cards (vertical, rotated)
        int leftAICardsHeight = leftAI.size() * (cw + spacing) - spacing;
        int leftAIStartY = (h - leftAICardsHeight) / 2;

        // Highlight for Left AI
        if (gameState == GameState.LEFT_AI_TURN) {
            g2d.setColor(new Color(0, 150, 255, 80)); // Blue highlight
            g2d.fillRoundRect(spacing / 2, leftAIStartY - spacing, ch + spacing, leftAICardsHeight + 2 * spacing, 20, 20);
        }

        for (int i = 0; i < leftAI.size(); i++) {
            Card c = leftAI.get(i);
            Image img = new ImageIcon(getClass().getResource(c.getImagePath())).getImage();

            int y_pos = leftAIStartY + i * (cw + spacing);
            int cardCenterX = spacing + ch / 2;
            int cardCenterY = y_pos + cw / 2;

            AffineTransform oldTransform = g2d.getTransform();
            g2d.translate(cardCenterX, cardCenterY);
            g2d.rotate(Math.toRadians(90));
            g2d.drawImage(img, -cw / 2, -ch / 2, cw, ch, null);
            g2d.setTransform(oldTransform);
        }

        // Right AI Cards (vertical, rotated)
        int rightAICardsHeight = rightAI.size() * (cw + spacing) - spacing;
        int rightAIStartY = (h - rightAICardsHeight) / 2;

        // Highlight for Right AI
        if (gameState == GameState.RIGHT_AI_TURN) {
            g2d.setColor(new Color(255, 0, 150, 80)); // Magenta highlight
            g2d.fillRoundRect(w - spacing - ch - (spacing / 2), rightAIStartY - spacing, ch + spacing, rightAICardsHeight + 2 * spacing, 20, 20);
        }

        for (int i = 0; i < rightAI.size(); i++) {
            Card c = rightAI.get(i);
            Image img = new ImageIcon(getClass().getResource(c.getImagePath())).getImage();

            int y_pos = rightAIStartY + i * (cw + spacing);
            int cardCenterX = w - spacing - ch / 2;
            int cardCenterY = y_pos + cw / 2;

            AffineTransform oldTransform = g2d.getTransform();
            g2d.translate(cardCenterX, cardCenterY);
            g2d.rotate(Math.toRadians(-90));
            g2d.drawImage(img, -cw / 2, -ch / 2, cw, ch, null);
            g2d.setTransform(oldTransform);
        }

        // Player's Cards
        int numplayerHand = playerHand.size();
        int totalPlayerAreaWidth = 0;
        for (ArrayList<Card> hand : playerHand) {
            totalPlayerAreaWidth += hand.size() * (cw + spacing) - spacing;
        }
        totalPlayerAreaWidth += Math.max(0, (numplayerHand - 1) * (spacing * 3)); // Spacing between hands

        int currentX = (w - totalPlayerAreaWidth) / 2;
        int playerY = h - ch - spacing * 2;

        for (int i = 0; i < numplayerHand; i++) {
            ArrayList<Card> hand = playerHand.get(i);
            int handWidth = hand.size() * (cw + spacing) - spacing;

            // Highlight active hand
            if (i == activeHandIndex && gameState == GameState.PLAYER_TURN) {
                g2d.setColor(new Color(255, 215, 0, 80)); // Gold highlight
                g2d.fillRoundRect(currentX - spacing, playerY - spacing, handWidth + 2 * spacing, ch + 2 * spacing, 20, 20);
            }

            for (int j = 0; j < hand.size(); j++) {
                Card c = hand.get(j);
                java.net.URL imgUrl = getClass().getResource(c.getImagePath());
                if (imgUrl == null) {
                    // If image not found, print error and draw red box
                    System.err.println("ERROR: Card image not found -> " + c.getImagePath());
                    g2d.setColor(Color.RED);
                    g2d.fillRect(currentX + j * (cw + spacing), playerY, cw, ch);
                    g2d.setColor(Color.WHITE);
                    g2d.drawString(c.toString(), currentX + j * (cw + spacing) + 5, playerY + 20);
                } else {
                    // Draw image normally
                    Image img = new ImageIcon(imgUrl).getImage();
                    g2d.drawImage(img, currentX + j * (cw + spacing), playerY, cw, ch, null);
                }
            }
            currentX += handWidth + spacing * 3;
        }
        
        // Draw animating cards on top
        for (AnimatedCard ac : animatedCards) {
            // Use back of card for hidden dealer card during animation
            String imagePath = ac.destination == Destination.DEALER_HIDDEN ? "/cards/BACK.png" : ac.card.getImagePath();
            Image cardImage = new ImageIcon(getClass().getResource(imagePath)).getImage();
            g2d.drawImage(cardImage, (int)ac.x, (int)ac.y, cw, ch, null);
        }

        // Display Scores
        int fontSize = Math.max(12, (int) (20 * scale));
        g2d.setFont(new Font("Arial", Font.BOLD, fontSize));
        g2d.setColor(Color.WHITE);
        FontMetrics scoreFm = g2d.getFontMetrics();

        // Player Score
        currentX = (w - totalPlayerAreaWidth) / 2;
        for (int i = 0; i < numplayerHand; i++) {
            int tempSum = playerSum.get(i);
            int tempAces = playerAce.get(i);
            while (tempSum > 21 && tempAces > 0) {
                tempSum -= 10;
                tempAces--;
            }
            String handText = numplayerHand > 1 ? String.format(tr("hand_prefix"), (i + 1)) : tr("player_label");
            g2d.drawString(handText + tempSum, currentX, playerY - spacing);
            
            if (playerBets.get(i) > 0) {
                g2d.drawString(tr("bet_label") + playerBets.get(i), currentX, playerY - spacing * 2 - scoreFm.getAscent());
            }

            ArrayList<Card> hand = playerHand.get(i);
            currentX += (hand.size() * (cw + spacing) - spacing) + spacing * 3;
        }

        // Dealer Score
        if (standButton.isEnabled()) {
            int visibleDealerSum = 0;
            int visibleDealerAceCount = 0;
            for (Card card : dealerHand) {
                visibleDealerSum += card.getValue();
                if (card.isAce()) {
                    visibleDealerAceCount++;
                }
            }
            while (visibleDealerSum > 21 && visibleDealerAceCount > 0) {
                visibleDealerSum -= 10;
                visibleDealerAceCount--;
            }
            g2d.drawString(tr("dealer_label") + visibleDealerSum, dealerStartX, dealerY + ch + spacing + scoreFm.getAscent());
        } else {
            int finalDealerSum = getEffectiveSum(dealerSum, dealerAce);
            g2d.drawString(tr("dealer_label") + finalDealerSum, dealerStartX, dealerY + ch + spacing + scoreFm.getAscent());
        }

        // Game Result
        if (!standButton.isEnabled() && !handResults.isEmpty()) {
            int resultFontSize = Math.max(20, (int) (48 * scale));
            g2d.setFont(new Font("Arial", Font.BOLD, resultFontSize));
            FontMetrics fm = g2d.getFontMetrics();

            int lineSpacing = (int) (10 * scale);
            int totalTextHeight = (handResults.size() * fm.getHeight()) + (Math.max(0, handResults.size() - 1) * lineSpacing);
            int maxWidth = 0;
            for (String result : handResults) {
                maxWidth = Math.max(maxWidth, fm.stringWidth(result));
            }
            int boxPadding = (int) (30 * scale);
            int boxWidth = maxWidth + boxPadding * 2;
            int boxHeight = totalTextHeight + boxPadding;
            int boxX = (w - boxWidth) / 2;
            int boxY = (h - boxHeight) / 2;

            // Draw a semi-transparent background for the results
            g2d.setColor(new Color(0, 0, 0, 150));
            g2d.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 30, 30);
            g2d.setColor(new Color(255, 255, 255, 50));
            g2d.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 30, 30);

            int yPos = boxY + fm.getAscent() + (boxPadding / 2);

            for (String result : handResults) {
                int msgWidth = fm.stringWidth(result);
                int xPos = (w - msgWidth) / 2;

                // Color adjustment
                Color textColor;
                // Check for win conditions
                if (result.contains("Win") || result.contains("Kazandın") || result.contains("Blackjack") || result.contains("CONGRATULATIONS") || result.contains("TEBRİKLER")) {
                    // But make sure it's not a "Dealer Blackjack, you lose" situation
                    if (result.contains("Lose") || result.contains("Kaybettin")) {
                         textColor = new Color(255, 80, 80); // Red for loss
                    } else {
                         textColor = new Color(100, 255, 100); // Green for win
                    }
                } else if (result.contains("Lose") || result.contains("Kaybettin") || result.contains("Bust") || result.contains("GAME OVER") || result.contains("OYUN BİTTİ")) {
                    textColor = new Color(255, 80, 80); // Brighter Red
                } else if (result.contains("Push") || result.contains("Berabere")) {
                    textColor = new Color(255, 255, 100); // Brighter Yellow
                } else {
                    textColor = Color.WHITE;
                }
                g2d.setColor(Color.BLACK);
                g2d.drawString(result, xPos + 3, yPos + 3);
                g2d.setColor(textColor);
                g2d.drawString(result, xPos, yPos);
                yPos += fm.getHeight() + lineSpacing;
            }
        }

        // Balance display
        g2d.setFont(new Font("Arial", Font.BOLD, fontSize));
        g2d.setColor(Color.WHITE);
        String balanceText = tr("balance_label") + playerBalance;
        g2d.drawString(balanceText, spacing, h - spacing);

        // AI Scores and Balances
        int tempLeftAISum = leftAISum;
        int tempLeftAIAceCount = leftAIAce;
        while (tempLeftAISum > 21 && tempLeftAIAceCount > 0) {
            tempLeftAISum -= 10;
            tempLeftAIAceCount--;
        }
        
        // Left AI info
        int leftInfoX = spacing + ch + spacing;
        int leftInfoY = leftAIStartY;
        g2d.drawString(tr("left_ai_label") + tempLeftAISum, leftInfoX, leftInfoY);
        g2d.drawString(tr("balance_label") + leftAIBalance, leftInfoX, leftInfoY + scoreFm.getAscent() + 5);
        if (leftAICurrentBet > 0) {
            g2d.drawString(tr("bet_label") + leftAICurrentBet, leftInfoX, leftInfoY + (scoreFm.getAscent() * 2) + 10);
        }
        int tempRightAISum = rightAISum;
        int tempRightAIAceCount = rightAIAce;
        while (tempRightAISum > 21 && tempRightAIAceCount > 0) {
            tempRightAISum -= 10;
            tempRightAIAceCount--;
        }

        // Right AI info
        String rightText1 = tr("right_ai_label") + tempRightAISum;
        String rightText2 = tr("balance_label") + rightAIBalance;
        String rightText3 = tr("bet_label") + rightAICurrentBet;

        int w1 = scoreFm.stringWidth(rightText1);
        int w2 = scoreFm.stringWidth(rightText2);
        int w3 = rightAICurrentBet > 0 ? scoreFm.stringWidth(rightText3) : 0;
        int maxTextWidth = Math.max(w1, Math.max(w2, w3));

        int rightInfoX = w - spacing - ch - spacing - maxTextWidth;
        int rightInfoY = rightAIStartY;
        g2d.drawString(rightText1, rightInfoX, rightInfoY);
        g2d.drawString(rightText2, rightInfoX, rightInfoY + scoreFm.getAscent() + 5);
        if (rightAICurrentBet > 0) {
            g2d.drawString(rightText3, rightInfoX, rightInfoY + (scoreFm.getAscent() * 2) + 10);
        }

    } catch (Exception e) {
        e.printStackTrace();
    } finally {
        g2d.dispose();
    }
} 
    };

    // Button panel
    JPanel buttonPanel = new JPanel();
    JButton hitButton = new JButton("Hit");
    JButton standButton = new JButton("Stand");
    JButton splitButton = new JButton("Split");
    JButton doubleDownButton = new JButton("Double Down");
    JButton newGameButton = new JButton("New Round");

    public BlackScrolls() {
        initializeTranslations();
        if (!isSoundtrackMuted) {
            playSoundtrack("/assets/soundtrack.wav");
        }

        // Load the background image
        try {
            backgroundImage = new ImageIcon(getClass().getResource("/assets/background.jpg")).getImage();
            // Load and scale icons for the mute button
            Image speakerImg = new ImageIcon(getClass().getResource("/assets/speaker.png")).getImage();
            Image mutedImg = new ImageIcon(getClass().getResource("/assets/speaker-muted.png")).getImage();
            speakerIcon = new ImageIcon(speakerImg.getScaledInstance(25, 25, Image.SCALE_SMOOTH));
            mutedIcon = new ImageIcon(mutedImg.getScaledInstance(25, 25, Image.SCALE_SMOOTH));
        } catch (Exception e) {
            System.err.println("Asset image not found. Check 'assets' folder for background.jpg, speaker.png, and speaker-muted.png.");
            backgroundImage = null;
        }

        // Tutorial button
        tutorialButton = new JButton("?");
        tutorialButton.setFont(new Font("Arial", Font.BOLD, 20));
        tutorialButton.setMargin(new Insets(0, 0, 0, 0));
        tutorialButton.setBounds(20, 20, 40, 40);
        tutorialButton.setFocusable(false);
        tutorialButton.addActionListener(e -> {
            playSound("/assets/button_click.wav");
            showTutorialDialog();
        });

        // Turkish button
        trButton = new JButton("TR");
        trButton.setFont(new Font("Arial", Font.BOLD, 12));
        trButton.setMargin(new Insets(0, 0, 0, 0));
        trButton.setBounds(70, 20, 40, 40);
        trButton.setFocusable(false);
        trButton.addActionListener(e -> {
            playSound("/assets/button_click.wav");
            setLanguage("tr");
        });

        // English button
        enButton = new JButton("EN");
        enButton.setFont(new Font("Arial", Font.BOLD, 12));
        enButton.setMargin(new Insets(0, 0, 0, 0));
        enButton.setBounds(120, 20, 40, 40);
        enButton.setFocusable(false);
        enButton.addActionListener(e -> {
            playSound("/assets/button_click.wav");
            setLanguage("en");
        });

        // Mute button
        muteButton = new JButton();
        muteButton.setBounds(170, 20, 40, 40);
        muteButton.setFocusable(false);
        muteButton.setMargin(new Insets(0, 0, 0, 0));
        updateMuteButtonIcon(); // Set initial icon and tooltip

        muteButton.addActionListener(e -> {
            playSound("/assets/button_click.wav");
            isSoundtrackMuted = !isSoundtrackMuted;
            updateMuteButtonIcon();
            if (isSoundtrackMuted) {
                if (soundtrackClip != null && soundtrackClip.isRunning()) {
                    soundtrackClip.stop();
                }
            } else {
                playSoundtrack("/assets/soundtrack.wav");
            }
        });

        gamePanel.setLayout(null); // Allow absolute positioning for the tutorial button
        gamePanel.add(tutorialButton);
        gamePanel.add(trButton);
        gamePanel.add(enButton);
        gamePanel.add(muteButton);

        startGame();

        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(gamePanel, BorderLayout.CENTER);

        buttonPanel.setBackground(new Color(30,35,45));
        buttonPanel.add(hitButton);
        buttonPanel.add(standButton);
        buttonPanel.add(splitButton);
        buttonPanel.add(doubleDownButton);
        buttonPanel.add(newGameButton);
        frame.add(buttonPanel, BorderLayout.SOUTH);

        hitButton.setFocusable(false);
        standButton.setFocusable(false);
        splitButton.setFocusable(false);
        doubleDownButton.setFocusable(false);
        newGameButton.setFocusable(false);

        hitButton.addActionListener(e -> {
            playSound("/assets/button_click.wav");
            splitButton.setEnabled(false);
            doubleDownButton.setEnabled(false);
            hitButton.setEnabled(false); // Disable immediately
            standButton.setEnabled(false);

            if (deck.isEmpty()) return;
            dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.PLAYER, activeHandIndex, "/assets/card_hit.wav"));

            startAnimation(() -> {
                // This code runs after the animation is finished
                if (getEffectiveSum(playerSum.get(activeHandIndex), playerAce.get(activeHandIndex)) > 21) {
                    moveToNextHandOrFinish();
                } else {
                    // Re-enable buttons if not bust
                    hitButton.setEnabled(true);
                    standButton.setEnabled(true);
                }
                gamePanel.repaint();
            });
        });

        standButton.addActionListener(e -> {
            playSound("/assets/button_click.wav");
            moveToNextHandOrFinish();
        });

        splitButton.addActionListener(e -> {
            playSound("/assets/button_click.wav");
            // Disable all action buttons during split
            splitButton.setEnabled(false);
            doubleDownButton.setEnabled(false);
            hitButton.setEnabled(false);
            standButton.setEnabled(false);

            int bet = playerBets.get(0);
            playerBalance -= bet;
            playerBets.add(bet);

            ArrayList<Card> firstHand = playerHand.get(0);
            Card secondCardOfFirstHand = firstHand.remove(1);

            ArrayList<Card> secondHand = new ArrayList<>();
            secondHand.add(secondCardOfFirstHand);
            playerHand.add(secondHand);

            playerSum.add(0);
            playerAce.add(0);

            recalculateHand(0);
            recalculateHand(1);

            // Queue the two new cards for animation
            String hitSound = "/assets/card_hit.wav";
            if (!deck.isEmpty()) {
                dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.PLAYER, 0, hitSound));
            }
            if (!deck.isEmpty()) {
                dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.PLAYER, 1, hitSound));
            }

            startAnimation(() -> {
                activeHandIndex = 0;

                // Re-enable buttons for the first hand
                hitButton.setEnabled(true);
                standButton.setEnabled(true);
                if (playerBalance >= playerBets.get(activeHandIndex)) {
                    doubleDownButton.setEnabled(true);
                }
                gamePanel.repaint();
            });
        });

        doubleDownButton.addActionListener(e -> {
            playSound("/assets/button_click.wav");
            int bet = playerBets.get(activeHandIndex);
            playerBalance -= bet;
            playerBets.set(activeHandIndex, bet * 2);
            
            hitButton.setEnabled(false);
            standButton.setEnabled(false);
            splitButton.setEnabled(false);
            doubleDownButton.setEnabled(false);

            if (deck.isEmpty()) return;
            dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.PLAYER, activeHandIndex, "/assets/card_hit.wav"));

            startAnimation(this::moveToNextHandOrFinish);
        });

        newGameButton.addActionListener(e -> {
            playSound("/assets/button_click.wav");
            if (isGameOver) {
                startGame();
            } else {
                startRound();
            }
        });

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (soundtrackClip != null) {
                    soundtrackClip.stop();
                    soundtrackClip.close();
                }
            }
        });


        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                gamePanel.repaint();
            }
        });

        frame.setVisible(true);

        updateUIForLanguage();
    }

    public void startGame() {
        playerBalance = 1000;
        leftAIBalance = 1000;
        rightAIBalance = 1000;
        startRound();
    }

    public void startRound() {
        playSound("/assets/new_game.wav");

        isGameOver = false;
        handResults = new ArrayList<>();
        gameState = GameState.PLAYER_TURN;
        leftAICurrentBet = 0;
        rightAICurrentBet = 0;

        buildDeck();
        shuffleDeck();

        // Dealer
        dealerHand = new ArrayList<>();
        dealerSum=0; dealerAce=0;

        // Player
        playerHand = new ArrayList<>();
        playerHand.add(new ArrayList<>());
        playerSum = new ArrayList<>();
        playerSum.add(0);
        playerAce = new ArrayList<>();
        playerAce.add(0);
        playerBets = new ArrayList<>();
        playerBets.add(0);
        activeHandIndex = 0;

        // AI
        leftAI = new ArrayList<>(); leftAISum=0; leftAIAce=0;
        rightAI = new ArrayList<>(); rightAISum=0; rightAIAce=0;

        hitButton.setEnabled(false);
        standButton.setEnabled(false);
        splitButton.setEnabled(false);
        doubleDownButton.setEnabled(false);
        newGameButton.setVisible(false);

        gamePanel.repaint();
        showBettingDialog();
    }
    
    // Betting Window
    private void showBettingDialog() {
        JDialog betDialog = new JDialog(frame, tr("bet_dialog_title"), true);
        betDialog.setLayout(new BorderLayout(10, 10));
        betDialog.setLocationRelativeTo(frame);
        betDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel balanceInfoLabel = new JLabel(String.format(tr("balance_info"), playerBalance), SwingConstants.CENTER);
        balanceInfoLabel.setFont(new Font("Arial", Font.BOLD, 18));
        contentPanel.add(balanceInfoLabel, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel();
        inputPanel.add(new JLabel(tr("bet_amount_label")));
        JTextField dialogBetField = new JTextField(8);
        dialogBetField.setFont(new Font("Arial", Font.PLAIN, 16));
        inputPanel.add(dialogBetField);
        JButton placeBetButton = new JButton(tr("place_bet_button"));
        inputPanel.add(placeBetButton);
        contentPanel.add(inputPanel, BorderLayout.CENTER);

        JPanel quickBetPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        quickBetPanel.add(new JLabel(tr("quick_bets_label")));
        JButton quick100 = new JButton("$100");
        JButton quick250 = new JButton("$250");
        JButton quick500 = new JButton("$500");
        JButton quick1000 = new JButton("$1000");
        quickBetPanel.add(quick100);
        quickBetPanel.add(quick250);
        quickBetPanel.add(quick500);
        quickBetPanel.add(quick1000);
        contentPanel.add(quickBetPanel, BorderLayout.SOUTH);

        betDialog.add(contentPanel);

        Runnable processBet = () -> {
            playSound("/assets/button_click.wav");
            try {
                int betAmount = Integer.parseInt(dialogBetField.getText());
                if (betAmount > 0 && betAmount <= playerBalance) {
                    playerBalance -= betAmount;
                    playerBets.set(0, betAmount);

                    if (leftAIBalance >= 100) {
                        int minBet = 100;
                        int maxBet = leftAIBalance;
                        int betStep = 100;
                        int randomBet = minBet + random.nextInt((maxBet - minBet) / betStep + 1) * betStep;
                        leftAICurrentBet = Math.min(leftAIBalance, randomBet);
                        leftAIBalance -= leftAICurrentBet;
                    }

                    if (rightAIBalance >= 50) {
                        int minBet = 50;
                        int maxBet = 250;
                        int betStep = 10;
                        int randomBet = minBet + random.nextInt((maxBet - minBet) / betStep + 1) * betStep;
                        rightAICurrentBet = Math.min(rightAIBalance, randomBet);
                        rightAIBalance -= rightAICurrentBet;
                    }

                    betDialog.dispose();
                    startDealingAnimation();
                } else {
                    JOptionPane.showMessageDialog(betDialog, "Invalid bet amount.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(betDialog, "Please enter a valid number for the bet.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        };

        placeBetButton.addActionListener(e -> processBet.run());
        dialogBetField.addActionListener(e -> processBet.run());

        quick100.addActionListener(e -> { dialogBetField.setText("100"); placeBetButton.doClick(); });
        quick250.addActionListener(e -> { dialogBetField.setText("250"); placeBetButton.doClick(); });
        quick500.addActionListener(e -> { dialogBetField.setText("500"); placeBetButton.doClick(); });
        quick1000.addActionListener(e -> { dialogBetField.setText("1000"); placeBetButton.doClick(); });

        betDialog.pack();
        betDialog.setVisible(true);
    }

    // Tutorial
    private void showTutorialDialog() {
        JDialog tutorialDialog = new JDialog(frame, tr("tutorial_title"), true);
        tutorialDialog.setSize(650, 600);
        tutorialDialog.setLocationRelativeTo(frame);
        tutorialDialog.setLayout(new BorderLayout());

        String htmlContent = "<html>"
            + "<body style='font-family: Arial, sans-serif; padding: 15px;'>"
            + "<h1 style='color: #2c3e50;'>" + tr("tutorial_title") + "</h1>"
            + "<h2 style='color: #34495e; border-bottom: 1px solid #ccc; padding-bottom: 5px;'>" + tr("tutorial_aim_title") + "</h2>"
            + "<p style='line-height: 1.6;'>"
            + tr("tutorial_aim_text")
            + "</p>"
            + "<h2 style='color: #34495e; border-bottom: 1px solid #ccc; padding-bottom: 5px;'>" + tr("tutorial_values_title") + "</h2>"
            + "<ul style='line-height: 1.6;'>"
            + "<li><b>" + tr("card_ace") + ":</b> " + tr("tutorial_values_ace") + "</li>"
            + "<li><b>" + tr("card_face") + ":</b> " + tr("tutorial_values_face") + "</li>"
            + "<li><b>" + tr("card_number") + ":</b> " + tr("tutorial_values_number") + "</li>"
            + "</ul>"
            + "<h2 style='color: #34495e; border-bottom: 1px solid #ccc; padding-bottom: 5px;'>" + tr("tutorial_moves_title") + "</h2>"
            + "<ul style='line-height: 1.6;'>"
            + "<li><b>" + tr("hit_button") + ":</b> " + tr("tutorial_moves_hit") + "</li>"
            + "<li><b>" + tr("stand_button") + ":</b> " + tr("tutorial_moves_stand") + "</li>"
            + "<li><b>" + tr("double_down_button") + ":</b> " + tr("tutorial_moves_double") + "</li>"
            + "<li><b>" + tr("split_button") + ":</b> " + tr("tutorial_moves_split") + "</li>"
            + "</ul>"
            + "<h2 style='color: #34495e; border-bottom: 1px solid #ccc; padding-bottom: 5px;'>" + tr("tutorial_win_loss_title") + "</h2>"
            + "<ul style='line-height: 1.6;'>"
            + "<li><b>Blackjack:</b> " + tr("tutorial_win_loss_blackjack") + "</li>"
            + "<li><b>" + tr("result_win_title") + ":</b> " + tr("tutorial_win_loss_win") + "</li>"
            + "<li><b>" + tr("result_lose_title") + ":</b> " + tr("tutorial_win_loss_lose") + "</li>"
            + "<li><b>" + tr("result_push_title") + ":</b> " + tr("tutorial_win_loss_push") + "</li>"
            + "</ul>"
            + "</body></html>";

        JEditorPane editorPane = new JEditorPane("text/html", htmlContent);
        editorPane.setEditable(false);
        editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        tutorialDialog.add(new JScrollPane(editorPane), BorderLayout.CENTER);
        tutorialDialog.setVisible(true);
    }

    private void startAnimation(Runnable onFinish) {
        lastDealTime = System.currentTimeMillis();

        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }

        animationTimer = new Timer(16, e -> { // ~60 FPS
            // Update positions of all currently animating cards
            if (!animatedCards.isEmpty()) {
                animatedCards.forEach(AnimatedCard::update);
            }

            // Remove finished cards from animation list and add them to their final hands
            animatedCards.removeIf(ac -> {
                if (ac.isFinished()) {
                    addCardToDestination(ac.card, ac.destination, ac.handIndex);
                    return true;
                }
                return false;
            });

            // Check if it's time to deal the next card
            long currentTime = System.currentTimeMillis();
            if (!dealQueue.isEmpty() && (animatedCards.isEmpty() || (currentTime - lastDealTime) > DEAL_DELAY)) {
                lastDealTime = currentTime;
                DealOrder nextDeal = dealQueue.remove(0);
                animateCard(nextDeal.card, nextDeal.destination, nextDeal.handIndex, nextDeal.soundFile);
            }

            gamePanel.repaint();

            // If all dealing and animations are done, stop and proceed
            if (dealQueue.isEmpty() && animatedCards.isEmpty()) {
                animationTimer.stop();
                if (onFinish != null) {
                    onFinish.run();
                }
            }
        });
        animationTimer.start();
    }

    private void startDealingAnimation() {
        buildDealQueue();
        startAnimation(this::finishInitialDeal);
    }

    private void animateCard(Card card, Destination destination, int handIndex, String soundFile) {
        int w = gamePanel.getWidth();
        int h = gamePanel.getHeight();
        double scale = Math.min(w / 1200.0, h / 800.0);
        int cw = (int) (cardWidth * scale);
        int spacing = Math.max(5, (int) (10 * scale));

        double startX = w - cw - spacing;
        double startY = spacing;

        Point targetPoint = getCardTargetCoordinates(destination, handIndex);
        AnimatedCard ac = new AnimatedCard(card, startX, startY, targetPoint.x, targetPoint.y, destination, handIndex);
        animatedCards.add(ac);
        playSound(soundFile);
    }

    private void finishInitialDeal() {
        boolean playerHasBlackjack = getEffectiveSum(playerSum.get(0), playerAce.get(0)) == 21;
        boolean dealerHasBlackjack = getEffectiveSum(dealerSum, dealerAce) == 21;

        if (playerHasBlackjack || dealerHasBlackjack) {
            hitButton.setEnabled(false);
            standButton.setEnabled(false);
            payout();
            newGameButton.setVisible(true);
        } else {
            hitButton.setEnabled(true);
            standButton.setEnabled(true);
            if (playerBalance >= playerBets.get(0)) doubleDownButton.setEnabled(true);
            ArrayList<Card> initialHand = playerHand.get(0);
            boolean canSplit = initialHand.size() == 2 && initialHand.get(0).getValue() == initialHand.get(1).getValue();
            if (canSplit && playerBalance >= playerBets.get(0)) splitButton.setEnabled(true);
        }
        gamePanel.repaint();
    }

    private Point getCardTargetCoordinates(Destination destination, int handIndex) {
        int w = gamePanel.getWidth();
        int h = gamePanel.getHeight();
        double scale = Math.min(w / 1200.0, h / 800.0);
        int cw = (int) (cardWidth * scale);
        int ch = (int) (cardHeight * scale);
        int spacing = Math.max(5, (int) (10 * scale));

        switch (destination) {
            case PLAYER:
                int numPlayerHands = playerHand.size();
                int totalPlayerAreaWidth = 0;
                for (ArrayList<Card> hand : playerHand) {
                    totalPlayerAreaWidth += hand.size() * (cw + spacing) - spacing;
                }
                totalPlayerAreaWidth += Math.max(0, (numPlayerHands - 1) * (spacing * 3));
                int playerStartX = (w - totalPlayerAreaWidth) / 2;
                int currentHandX = playerStartX;
                for (int i = 0; i < handIndex; i++) {
                    currentHandX += (playerHand.get(i).size() * (cw + spacing) - spacing) + spacing * 3;
                }
                int cardX = currentHandX + playerHand.get(handIndex).size() * (cw + spacing);
                return new Point(cardX, h - ch - spacing * 2);
            case DEALER_VISIBLE:
                return new Point((w - (2 * (cw + spacing) - spacing)) / 2 + (dealerHand.size() * (cw + spacing)), spacing);
            case DEALER_HIDDEN:
                return new Point((w - (2 * (cw + spacing) - spacing)) / 2, spacing);
            case LEFT_AI:
                int leftAICardsHeight = leftAI.size() * (cw + spacing);
                int leftAIStartY = (h - leftAICardsHeight) / 2;
                return new Point(spacing, leftAIStartY + leftAI.size() * (cw + spacing));
            case RIGHT_AI:
                int rightAICardsHeight = rightAI.size() * (cw + spacing);
                int rightAIStartY = (h - rightAICardsHeight) / 2;
                return new Point(w - spacing - ch, rightAIStartY + rightAI.size() * (cw + spacing));
        }
        return new Point(0, 0); // Should not happen
    }

    private void moveToNextHandOrFinish() {
        activeHandIndex++;
        if (activeHandIndex < playerHand.size()) {
            // Move to next hand
            hitButton.setEnabled(true);
            standButton.setEnabled(true);
            doubleDownButton.setEnabled(false); // Default to false
            if (playerBalance >= playerBets.get(activeHandIndex)) {
                doubleDownButton.setEnabled(true);
            }
            gamePanel.repaint();
        } else {
            // All player hands are played, finish round
            finishRoundAfterPlayer();
        }
    }

    private void processGameFlow() {
        if (deck.isEmpty()) {
            gameState = GameState.PAYOUT;
        }
    
        String hitSound = "/assets/card_hit.wav";
    
        switch (gameState) {
            case LEFT_AI_TURN:
                if (leftAIBalance > 0 && shouldAIHit(leftAISum, leftAIAce, dealerHand.get(0).getValue())) {
                    dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.LEFT_AI, 0, hitSound));
                    startAnimation(this::processGameFlowWithDelay);
                } else {
                    gameState = GameState.RIGHT_AI_TURN;
                    processGameFlowWithDelay();
                }
                break;
            case RIGHT_AI_TURN:
                if (rightAIBalance > 0 && shouldAIHit(rightAISum, rightAIAce, dealerHand.get(0).getValue())) {
                    dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.RIGHT_AI, 0, hitSound));
                    startAnimation(this::processGameFlowWithDelay);
                } else {
                    gameState = GameState.DEALER_TURN;
                    processGameFlowWithDelay();
                }
                break;
            case DEALER_TURN:
                if (hiddenCard != null) {
                    dealerHand.add(0, hiddenCard);
                    hiddenCard = null;
                    gamePanel.repaint();
                    processGameFlowWithDelay(); // Wait a second before the first action
                    return; // Exit this tick, the timer will call us back
                }
    
                if (getEffectiveSum(dealerSum, dealerAce) < 17) {
                    dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.DEALER_VISIBLE, 0, hitSound));
                    startAnimation(this::processGameFlowWithDelay);
                } else {
                    gameState = GameState.PAYOUT;
                    processGameFlow(); // No delay needed before payout
                }
                break;
            case PAYOUT:
                payout();
                newGameButton.setVisible(true);
                break;
            default:
                // Should not happen
                break;
        }
        gamePanel.repaint();
    }
    
    private void processGameFlowWithDelay() {
        Timer delayTimer = new Timer(1000, e -> processGameFlow());
        delayTimer.setRepeats(false);
        delayTimer.start();
    }

    private void finishRoundAfterPlayer() {
        hitButton.setEnabled(false);
        standButton.setEnabled(false);
        splitButton.setEnabled(false);
        doubleDownButton.setEnabled(false);

        gameState = GameState.LEFT_AI_TURN;
        processGameFlowWithDelay(); // Start the AI/Dealer turn 
        gamePanel.repaint();
    }

    public void buildDealQueue() {
        dealQueue.clear();
        String dealSound = "/assets/card_deal.wav";
        // Standard dealing order: Player --> Left AI --> Right AI --> Dealer
        dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.PLAYER, 0, dealSound));
        dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.LEFT_AI, 0, dealSound));
        dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.RIGHT_AI, 0, dealSound));
        dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.DEALER_VISIBLE, 0, dealSound));

        dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.PLAYER, 0, dealSound));
        dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.LEFT_AI, 0, dealSound));
        dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.RIGHT_AI, 0, dealSound));
        dealQueue.add(new DealOrder(deck.remove(deck.size() - 1), Destination.DEALER_HIDDEN, 0, dealSound));
    }

    private void addCardToDestination(Card card, Destination destination, int handIndex) {
        switch (destination) {
            case PLAYER:
                playerHand.get(handIndex).add(card);
                recalculateHand(handIndex);
                break;
            case DEALER_VISIBLE:
                dealerHand.add(card);
                dealerSum += card.getValue();
                dealerAce += card.isAce() ? 1 : 0;
                break;
            case DEALER_HIDDEN:
                hiddenCard = card;
                dealerSum += card.getValue();
                dealerAce += card.isAce() ? 1 : 0;
                break;
            case LEFT_AI:
                leftAI.add(card);
                leftAISum += card.getValue();
                leftAIAce += card.isAce() ? 1 : 0;
                break;
            case RIGHT_AI:
                rightAI.add(card);
                rightAISum += card.getValue();
                rightAIAce += card.isAce() ? 1 : 0;
                break;
        }
    }

public void payout() {
    handResults.clear();
    ArrayList<HandOutcome> outcomes = new ArrayList<>();

    // Dealer's final hand
    int finalDealerSum = getEffectiveSum(dealerSum, dealerAce);
    int totalDealerCards = dealerHand.size() + (hiddenCard != null ? 1 : 0);
    boolean dealerHasBlackjack = finalDealerSum == 21 && totalDealerCards == 2;
    
    for (int i = 0; i < playerHand.size(); i++) {
        int finalPlayerSum = getEffectiveSum(playerSum.get(i), playerAce.get(i));
        int bet = playerBets.get(i);
        boolean playerHasBlackjack = finalPlayerSum == 21 && playerHand.get(i).size() == 2 && playerHand.size() == 1;
        String resultMessage = "";
        HandOutcome outcome;

        if (finalPlayerSum > 21) {
            // Player busts - lose bet
            resultMessage = String.format(tr("result_bust_lose"), bet);
            outcome = HandOutcome.LOSE;
        } else if (playerHasBlackjack && !dealerHasBlackjack) {
            // Blackjack pays 3:2 - return bet + 1.5x bet as winnings
            int winnings = bet + (bet * 3 / 2);
            playerBalance += winnings;
            resultMessage = String.format(tr("result_blackjack_win"), (bet * 3 / 2));
            outcome = HandOutcome.WIN;
        } else if (dealerHasBlackjack && !playerHasBlackjack) {
            // Dealer blackjack - player loses
            resultMessage = String.format(tr("result_dealer_blackjack_lose"), bet);
            outcome = HandOutcome.LOSE;
        } else if (finalDealerSum > 21) {
            // Dealer busts - player wins 1:1
            int winnings = bet * 2;
            playerBalance += winnings;
            resultMessage = String.format(tr("result_dealer_busts_win"), bet);
            outcome = HandOutcome.WIN;
        } else if (finalPlayerSum > finalDealerSum) {
            // Player wins 1:1
            int winnings = bet * 2;
            playerBalance += winnings;
            resultMessage = String.format(tr("result_win"), bet);
            outcome = HandOutcome.WIN;
        } else if (finalPlayerSum == finalDealerSum) {
            // Push - return original bet only
            playerBalance += bet;
            resultMessage = String.format(tr("result_push"), bet);
            outcome = HandOutcome.PUSH;
        } else {
            // Player loses
            resultMessage = String.format(tr("result_lose"), bet);
            outcome = HandOutcome.LOSE;
        }
        outcomes.add(outcome);
        String handPrefix = playerHand.size() > 1 ? String.format(tr("hand_prefix"), (i + 1)) : "";
        handResults.add(handPrefix + resultMessage);
        
        // Debug output to help track the issue
        System.out.println("Hand " + (i+1) + " - Bet: $" + bet + ", Result: " + resultMessage + ", Balance now: $" + playerBalance);
    }

    if (outcomes.contains(HandOutcome.WIN)) {
        playSound("/assets/win.wav");
    } else if (!outcomes.contains(HandOutcome.LOSE)) { // Only PUSHes
        playSound("/assets/push.wav");
    } else { // Contains LOSE and possibly PUSH, but no WIN
        playSound("/assets/lose.wav");
    }

    // AI Payouts
    leftAIBalance += calculateAIPayout(leftAISum, leftAIAce, leftAI, leftAICurrentBet, finalDealerSum, dealerHasBlackjack);
    leftAICurrentBet = 0;

    rightAIBalance += calculateAIPayout(rightAISum, rightAIAce, rightAI, rightAICurrentBet, finalDealerSum, dealerHasBlackjack);
    rightAICurrentBet = 0;

    // After all payouts, check for game over condition
    checkGameOver();
}

private int calculateAIPayout(int aiSum, int aiAce, ArrayList<Card> aiHand, int aiBet, int finalDealerSum, boolean dealerHasBlackjack) {
    int finalAISum = getEffectiveSum(aiSum, aiAce);
    boolean aiHasBlackjack = finalAISum == 21 && aiHand.size() == 2;

    if (finalAISum > 21) {
        return 0; // AI busts, loses bet
    } else if (aiHasBlackjack && !dealerHasBlackjack) {
        return aiBet + (aiBet * 3 / 2); // Blackjack pays 3:2
    } else if (dealerHasBlackjack && !aiHasBlackjack) {
        return 0; // Dealer has blackjack, AI loses
    } else if (finalDealerSum > 21 || finalAISum > finalDealerSum) {
        return aiBet * 2; // AI wins 1:1
    } else if (finalAISum == finalDealerSum) {
        return aiBet; // Push
    } else { // finalAISum < finalDealerSum
        return 0; // AI loses
    }
}

private void checkGameOver() {
    if (playerBalance <= 0) {
        isGameOver = true;
        handResults.add(tr("result_game_over_lose"));
        newGameButton.setText(tr("new_game_button"));
    } else if (leftAIBalance <= 0 && rightAIBalance <= 0) {
        isGameOver = true;
        handResults.add(tr("result_game_over_win"));
        newGameButton.setText(tr("new_game_button"));
    } else {
        isGameOver = false;
        newGameButton.setText(tr("new_round_button"));
    }
}

    public void buildDeck() {
        deck = new ArrayList<>();
        String[] vals = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};
        String[] suits = {"S","H","D","C"};
        for(String s:suits) for(String v:vals) deck.add(new Card(v,s));
    }

    public void shuffleDeck() {
        for(int i=0;i<deck.size();i++) {
            int j = random.nextInt(deck.size());
            Card tmp = deck.get(i);
            deck.set(i, deck.get(j));
            deck.set(j, tmp);
        }
    }

    private void recalculateHand(int handIndex) {
        int sum = 0;
        int aces = 0;
        for (Card c : playerHand.get(handIndex)) {
            sum += c.getValue();
            if (c.isAce()) {
                aces++;
            }
        }
        playerSum.set(handIndex, sum);
        playerAce.set(handIndex, aces);
    }
    
    private int getEffectiveSum(int sum, int aceCount) {
        while (sum > 21 && aceCount > 0) {
            sum -= 10;
            aceCount--;
        }
        return sum;
    }

    private boolean isSoft(int sum, int aceCount) {
        while (sum > 21 && aceCount > 0) {
            sum -= 10;
            aceCount--;
        }
        return aceCount > 0;
    }

    private boolean shouldAIHit(int currentSum, int aceCount, int dealerUpCardValue) {
        boolean soft = isSoft(currentSum, aceCount);
        int effectiveSum = getEffectiveSum(currentSum, aceCount);

        if (effectiveSum >= 21) { // Already at 21 or bust
            return false;
        }

        if (soft) {
            // Soft Totals Strategy
            if (effectiveSum >= 19) { // Soft 19, 20
                return false; // Stand
            }
            if (effectiveSum == 18) { // Soft 18 (A,7)
                // Stand on dealer 2-8, otherwise Hit
                return !(dealerUpCardValue >= 2 && dealerUpCardValue <= 8);
            }
            // Soft 17 or less
            return true; // Always Hit
        } else {
            // Hard Totals Strategy
            if (effectiveSum >= 17) {
                return false; // Stand
            }
            if (effectiveSum >= 13 && effectiveSum <= 16) {
                // Stand on dealer 2-6, otherwise Hit
                return !(dealerUpCardValue >= 2 && dealerUpCardValue <= 6);
            }
            if (effectiveSum == 12) {
                // Stand on dealer 4-6, otherwise Hit
                return !(dealerUpCardValue >= 4 && dealerUpCardValue <= 6);
            }
            // 11 or less
            return true; // Always Hit
        }
    }

    private void playSound(String soundFilePath) {
        new Thread(() -> {
            try {
                URL soundURL = getClass().getResource(soundFilePath);
                if (soundURL == null) {
                    System.err.println("Sound file not found: " + soundFilePath);
                    return;
                }
                AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundURL);
                Clip clip = AudioSystem.getClip();
                clip.open(audioIn);
                clip.start();
            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void playSoundtrack(String soundFilePath) {
        if (isSoundtrackMuted) return;
        new Thread(() -> {
            try {
                if (soundtrackClip != null && soundtrackClip.isRunning()) {
                    soundtrackClip.stop();
                    soundtrackClip.close();
                }
                URL soundURL = getClass().getResource(soundFilePath);
                if (soundURL == null) {
                    System.err.println("Soundtrack file not found: " + soundFilePath);
                    return;
                }
                AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundURL);
                soundtrackClip = AudioSystem.getClip();
                soundtrackClip.open(audioIn);
                soundtrackClip.loop(Clip.LOOP_CONTINUOUSLY);
                soundtrackClip.start();
            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void updateMuteButtonIcon() {
        if (muteButton == null) return;
        if (isSoundtrackMuted) {
            muteButton.setIcon(mutedIcon);
            muteButton.setToolTipText(tr("unmute_tooltip"));
        } else {
            muteButton.setIcon(speakerIcon);
            muteButton.setToolTipText(tr("mute_tooltip"));
        }
    }


    // TRANSLATIONS
    private void initializeTranslations() {
        translations = new HashMap<>();

        // English
        Map<String, String> en = new HashMap<>();
        en.put("window_title", "BlackScrolls");
        en.put("hit_button", "Hit");
        en.put("stand_button", "Stand");
        en.put("split_button", "Split");
        en.put("double_down_button", "Double Down");
        en.put("new_round_button", "New Round");
        en.put("new_game_button", "Start New Game");
        en.put("tutorial_tooltip", "How to Play");
        en.put("mute_tooltip", "Mute Sound");
        en.put("unmute_tooltip", "Unmute Sound");
        en.put("bet_dialog_title", "Place Your Bet");
        en.put("balance_info", "Your Balance: $%d");
        en.put("bet_amount_label", "Bet Amount: $");
        en.put("place_bet_button", "Place Bet");
        en.put("quick_bets_label", "Quick Bets:");
        en.put("invalid_bet_error", "Invalid bet amount.");
        en.put("invalid_number_error", "Please enter a valid number for the bet.");
        en.put("error_title", "Error");
        en.put("result_bust_lose", "Bust! You Lose $%d");
        en.put("result_blackjack_win", "Blackjack! You Win $%d");
        en.put("result_dealer_blackjack_lose", "Dealer Blackjack. You Lose $%d");
        en.put("result_dealer_busts_win", "Dealer Busts! You Win $%d");
        en.put("result_win", "You Win $%d");
        en.put("result_push", "Push ($%d)");
        en.put("result_lose", "You Lose $%d");
        en.put("result_game_over_lose", "GAME OVER! You are out of money.");
        en.put("result_game_over_win", "CONGRATULATIONS! You beat the house!");
        en.put("hand_prefix", "Hand %d: ");
        en.put("player_label", "Player: ");
        en.put("dealer_label", "Dealer: ");
        en.put("left_ai_label", "Left AI: ");
        en.put("right_ai_label", "Right AI: ");
        en.put("balance_label", "Balance: $");
        en.put("bet_label", "Bet: $");
        en.put("tutorial_title", "How to Play Blackjack?");
        en.put("tutorial_aim_title", "Objective");
        en.put("tutorial_aim_text", "The goal is to get a hand total closer to 21 than the dealer without going over. If your hand exceeds 21, you <b>\"Bust\"</b> and lose automatically.");
        en.put("tutorial_values_title", "Card Values");
        en.put("card_ace", "Ace (A)");
        en.put("tutorial_values_ace", "Can be 1 or 11. The most advantageous value is automatically chosen for your hand.");
        en.put("card_face", "Face Cards (K, Q, J)");
        en.put("tutorial_values_face", "Are worth 10.");
        en.put("card_number", "Number Cards (2-10)");
        en.put("tutorial_values_number", "Are worth their face value.");
        en.put("tutorial_moves_title", "Player Moves");
        en.put("tutorial_moves_hit", "Request another card from the deck.");
        en.put("tutorial_moves_stand", "Stop taking cards and end your turn.");
        en.put("tutorial_moves_double", "Double your initial bet and receive only one more card. This is often a good move when your first two cards total 9, 10, or 11.");
        en.put("tutorial_moves_split", "If your first two cards have the same value (e.g., two 8s), you can split them into two separate hands. You must place a bet equal to your initial bet on the second hand.");
        en.put("tutorial_win_loss_title", "Winning and Losing");
        en.put("tutorial_win_loss_blackjack", "If your first two cards are an Ace and a 10-value card, it's a \"Blackjack\" and you usually win 3:2.");
        en.put("result_win_title", "Winning");
        en.put("tutorial_win_loss_win", "You win if your hand total is higher than the dealer's without exceeding 21.");
        en.put("result_lose_title", "Losing");
        en.put("tutorial_win_loss_lose", "You lose if your hand total exceeds 21 (Bust) or if the dealer's hand is higher than yours.");
        en.put("result_push_title", "Push (Tie)");
        en.put("tutorial_win_loss_push", "If your hand total is the same as the dealer's, your bet is returned.");
        translations.put("en", en);

        // Turkish
        Map<String, String> tr = new HashMap<>();
        tr.put("window_title", "BlackScrolls");
        tr.put("hit_button", "Kart Çek");
        tr.put("stand_button", "Dur");
        tr.put("split_button", "Böl");
        tr.put("double_down_button", "İkiye Katla");
        tr.put("new_round_button", "Yeni Tur");
        tr.put("new_game_button", "Yeni Oyuna Başla");
        tr.put("tutorial_tooltip", "Nasıl Oynanır");
        tr.put("mute_tooltip", "Sesi Kapat");
        tr.put("unmute_tooltip", "Sesi Aç");
        tr.put("bet_dialog_title", "Bahis Yap");
        tr.put("balance_info", "Bakiyeniz: $%d");
        tr.put("bet_amount_label", "Bahis Miktarı: $");
        tr.put("place_bet_button", "Bahis Yap");
        tr.put("quick_bets_label", "Hızlı Bahis:");
        tr.put("invalid_bet_error", "Geçersiz bahis miktarı.");
        tr.put("invalid_number_error", "Lütfen bahis için geçerli bir sayı girin.");
        tr.put("error_title", "Hata");
        tr.put("result_bust_lose", "Bust! $%d Kaybettin");
        tr.put("result_blackjack_win", "Blackjack! $%d Kazandın");
        tr.put("result_dealer_blackjack_lose", "Kurpiyer Blackjack. $%d Kaybettin");
        tr.put("result_dealer_busts_win", "Kurpiyer Bust! $%d Kazandın");
        tr.put("result_win", "$%d Kazandın!");
        tr.put("result_push", "Berabere ($%d)");
        tr.put("result_lose", "$%d Kaybettin.");
        tr.put("result_game_over_lose", "OYUN BİTTİ! Paran kalmadı.");
        tr.put("result_game_over_win", "TEBRİKLER! Kasayı yendin!");
        tr.put("hand_prefix", "El %d: ");
        tr.put("player_label", "Oyuncu: ");
        tr.put("dealer_label", "Kurpiyer: ");
        tr.put("left_ai_label", "Sol YZ: ");
        tr.put("right_ai_label", "Sağ YZ: ");
        tr.put("balance_label", "Bakiye: $");
        tr.put("bet_label", "Bahis: $");
        tr.put("tutorial_title", "Blackjack Nasıl Oynanır?");
        tr.put("tutorial_aim_title", "Oyunun Amacı");
        tr.put("tutorial_aim_text", "Amaç, elinizdeki kartların toplam değerini 21'e mümkün olduğunca yaklaştırmak ve kurpiyerin elini geçmektir. 21'i geçerseniz <b>\"Bust\"</b> olur ve otomatik olarak kaybedersiniz.");
        tr.put("tutorial_values_title", "Kart Değerleri");
        tr.put("card_ace", "As (A)");
        tr.put("tutorial_values_ace", "1 veya 11 değerinde olabilir. Elinizin toplamına göre en avantajlı olan değer otomatik olarak seçilir.");
        tr.put("card_face", "Papaz (K), Kız (Q), Vale (J)");
        tr.put("tutorial_values_face", "10 değerindedir.");
        tr.put("card_number", "Sayı Kartları (2-10)");
        tr.put("tutorial_values_number", "Üzerlerinde yazan sayı değerindedir.");
        tr.put("tutorial_moves_title", "Oyuncu Hamleleri");
        tr.put("tutorial_moves_hit", "Desteden yeni bir kart istersiniz.");
        tr.put("tutorial_moves_stand", "Kart çekmeyi bırakır ve sıranızı kurpiyere devredersiniz.");
        tr.put("tutorial_moves_double", "Başlangıç bahsinizi ikiye katlar ve sadece bir kart daha çekersiniz. Bu hamle genellikle ilk iki kartınızın toplamı 9, 10 veya 11 olduğunda avantajlıdır.");
        tr.put("tutorial_moves_split", "İlk iki kartınız aynı değere sahipse (örneğin iki 8'li), bunları iki ayrı ele bölebilirsiniz. İkinci el için de başlangıç bahsiniz kadar bir bahis koymanız gerekir.");
        tr.put("tutorial_win_loss_title", "Kazanma ve Kaybetme");
        tr.put("tutorial_win_loss_blackjack", "İlk iki kartınız bir As ve 10'luk bir kart ise, bu \"Blackjack\"tir ve genellikle 3'e 2 oranında kazanırsınız.");
        tr.put("result_win_title", "Kazanma");
        tr.put("tutorial_win_loss_win", "Elinizin toplamı 21'i geçmeden kurpiyerin elinden daha yüksekse kazanırsınız.");
        tr.put("result_lose_title", "Kaybetme");
        tr.put("tutorial_win_loss_lose", "Elinizin toplamı 21'i geçerse (Bust) veya kurpiyerin eli sizinkinden daha yüksekse kaybedersiniz.");
        tr.put("result_push_title", "Beraberlik (Push)");
        tr.put("tutorial_win_loss_push", "Elinizin toplamı kurpiyerin eliyle aynıysa, bahsiniz iade edilir.");
        translations.put("tr", tr);
    }

    private String tr(String key) {
        return translations.getOrDefault(currentLanguage, translations.get("en")).getOrDefault(key, key);
    }

    private void setLanguage(String lang) {
        this.currentLanguage = lang;
        updateUIForLanguage();
    }

    private void updateUIForLanguage() {
        hitButton.setText(tr("hit_button"));
        standButton.setText(tr("stand_button"));
        splitButton.setText(tr("split_button"));
        doubleDownButton.setText(tr("double_down_button"));
        newGameButton.setText(isGameOver ? tr("new_game_button") : tr("new_round_button"));
        tutorialButton.setToolTipText(tr("tutorial_tooltip"));
        updateMuteButtonIcon();
        frame.setTitle(tr("window_title"));
        gamePanel.repaint();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(BlackScrolls::new);
    }

}