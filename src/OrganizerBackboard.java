import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class OrganizerBackboard extends JFrame {
    private JPanel mainPanel;
    private Map<Integer, JLabel> poolLabels = new HashMap<>();
    private Map<Integer, JTable> poolTables = new HashMap<>();
    private JLabel eliminationTitleLabel;
    private EliminationTreePanel treePanel;

    public OrganizerBackboard() {
        setTitle("Fencer Backboard - Tournament Organizer");
        setSize(1280, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(Color.WHITE);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        add(scrollPane);
        setVisible(true);
    }
    
    public void displayPools(Map<Integer, List<String[]>> pools) {
        mainPanel.removeAll();
        poolLabels.clear();
        poolTables.clear();
        eliminationTitleLabel = null;
        treePanel = null;
        
        for (Map.Entry<Integer, List<String[]>> entry : pools.entrySet()) {
            int poolNumber = entry.getKey();
            List<String[]> pool = entry.getValue();

            JPanel poolPanel = new JPanel();
            poolPanel.setLayout(new BorderLayout());
            poolPanel.setBorder(BorderFactory.createEtchedBorder());
            poolPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            poolPanel.setMaximumSize(new Dimension(1100, 200));

            JLabel label = new JLabel("Pool " + poolNumber);
            label.setFont(new Font("Arial", Font.BOLD, 16));
            label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            poolPanel.add(label, BorderLayout.NORTH);
            poolLabels.put(poolNumber, label);

            String[] headers = new String[pool.size() + 4];
            headers[0] = "";
            for (int j = 0; j < pool.size(); j++) headers[j + 1] = pool.get(j)[0];
            headers[pool.size() + 1] = "V";
            headers[pool.size() + 2] = "SD";
            headers[pool.size() + 3] = "SR";

            Object[][] data = new String[pool.size()][headers.length];
            for (int row = 0; row < pool.size(); row++) {
                data[row][0] = pool.get(row)[0];
                for (int col = 1; col < headers.length; col++) data[row][col] = (col == row + 1) ? "X" : "";
            }

            DefaultTableModel model = new DefaultTableModel(data, headers);
            JTable table = new JTable(model);
            table.setEnabled(false);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            table.setRowHeight(25);
            table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
            table.setFont(new Font("Arial", Font.PLAIN, 12));

            DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
            centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

            table.getColumnModel().getColumn(0).setPreferredWidth(120);
            for (int col = 1; col < table.getColumnCount(); col++) {
                table.getColumnModel().getColumn(col).setCellRenderer(centerRenderer);
                table.getColumnModel().getColumn(col).setPreferredWidth(col <= pool.size() ? 80 : 40);
            }

            table.getColumnModel().getColumn(pool.size() + 1).setCellRenderer(new ColorRenderer(new Color(220, 255, 220)));
            table.getColumnModel().getColumn(pool.size() + 2).setCellRenderer(new ColorRenderer(new Color(220, 220, 255)));
            table.getColumnModel().getColumn(pool.size() + 3).setCellRenderer(new ColorRenderer(new Color(255, 220, 220)));

            JScrollPane tableScrollPane = new JScrollPane(table);
            poolPanel.add(tableScrollPane, BorderLayout.CENTER);
            mainPanel.add(poolPanel);
            mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
            poolTables.put(poolNumber, table);
        }
        revalidate();
        repaint();
    }

    public void updatePoolReferee(int poolNumber, String refereeName) {
        JLabel label = poolLabels.get(poolNumber);
        if (label != null) label.setText("Pool " + poolNumber + " - Referee: " + refereeName);
    }
    
    public void updateScore(int poolNumber, String fencer1Name, String score1, String fencer2Name, String score2) {
        JTable table = poolTables.get(poolNumber);
        if (table != null) {
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            int row1 = findFencerRow(model, fencer1Name);
            int col2 = findFencerColumn(table, fencer2Name);
            if (row1 != -1 && col2 != -1) model.setValueAt("5".equals(score1) ? "V" : score1, row1, col2);

            int row2 = findFencerRow(model, fencer2Name);
            int col1 = findFencerColumn(table, fencer1Name);
            if (row2 != -1 && col1 != -1) model.setValueAt("5".equals(score2) ? "V" : score2, row2, col1);
        }
    }
    
    public void updatePoolStats(int poolNumber, String fencerName, int victories, int hitsGiven, int hitsReceived) {
        JTable table = poolTables.get(poolNumber);
        if (table != null) {
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            int row = findFencerRow(model, fencerName);
            if (row != -1) {
                int vCol = model.getColumnCount() - 3;
                int sdCol = model.getColumnCount() - 2;
                int srCol = model.getColumnCount() - 1;
                model.setValueAt(String.valueOf(victories), row, vCol);
                model.setValueAt(String.valueOf(hitsGiven), row, sdCol);
                model.setValueAt(String.valueOf(hitsReceived), row, srCol);
            }
        }
    }

    public void displayEliminationRound(List<String[]> roundBouts) {
        if (treePanel == null) {
            eliminationTitleLabel = new JLabel("Direct Elimination Tree");
            eliminationTitleLabel.setFont(new Font("Arial", Font.BOLD, 20));
            eliminationTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            eliminationTitleLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
            mainPanel.add(eliminationTitleLabel);
            treePanel = new EliminationTreePanel();
            mainPanel.add(treePanel);
        }
        treePanel.addRound(roundBouts);
        revalidate();
        repaint();
    }
    
    public void displayFinalWinner(String winnerName) {
        if (treePanel != null) {
            treePanel.setFinalWinner(winnerName);
            revalidate();
            repaint();
        }
    }
    
    private int findFencerRow(DefaultTableModel model, String fencerName) {
        for (int row = 0; row < model.getRowCount(); row++) {
            if (fencerName.equals(model.getValueAt(row, 0))) return row;
        }
        return -1;
    }

    private int findFencerColumn(JTable table, String fencerName) {
        for (int col = 1; col < table.getColumnCount(); col++) {
            if (fencerName.equals(table.getColumnName(col))) return col;
        }
        return -1;
    }

    private static class ColorRenderer extends DefaultTableCellRenderer {
        private final Color backgroundColor;
        public ColorRenderer(Color backgroundColor) {
            this.backgroundColor = backgroundColor;
            setOpaque(true);
            setHorizontalAlignment(SwingConstants.CENTER);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(backgroundColor);
            return this;
        }
    }

    private class EliminationTreePanel extends JPanel {
        private final List<List<String[]>> allRounds = new ArrayList<>();
        private String finalWinner = null;

        private static final int BOX_WIDTH = 120;
        private static final int BOX_HEIGHT = 25;
        private static final int V_GAP = 20;
        private static final int H_GAP = 60;

        EliminationTreePanel() {
            setBackground(Color.WHITE);
        }

        public void addRound(List<String[]> newRound) {
            // Se non è il primo turno, completa il nuovo turno con i vincitori
            // dei bye del turno precedente.
            if (!allRounds.isEmpty()) {
                List<String> byeWinners = new ArrayList<>();
                List<String[]> lastRound = allRounds.get(allRounds.size() - 1);
                for (String[] bout : lastRound) {
                    if (bout[1] == null) {
                        byeWinners.add(bout[0]);
                    }
                }

                // Inserisci i vincitori dei bye nel nuovo turno
                List<String> newRoundFencers = new ArrayList<>();
                for (String[] bout : newRound) {
                    newRoundFencers.add(bout[0]);
                    if (bout[1] != null) {
                        newRoundFencers.add(bout[1]);
                    }
                }
                newRoundFencers.addAll(byeWinners);

                // Ricostruisci la lista degli assalti per il turno corrente
                List<String[]> completedNewRound = new ArrayList<>();
                for (int i = 0; i < newRoundFencers.size(); i += 2) {
                    String fencer1 = newRoundFencers.get(i);
                    String fencer2 = (i + 1 < newRoundFencers.size()) ? newRoundFencers.get(i + 1) : null;

                    // Anche se fencer2 è null, lo aggiungiamo per visualizzarlo nel turno successivo
                    completedNewRound.add(new String[]{fencer1, fencer2});
                }
                allRounds.add(completedNewRound);
            } else {
                allRounds.add(newRound);
            }
            updatePreferredSize();
        }
        
        public void setFinalWinner(String winnerName) {
            this.finalWinner = winnerName;
        }

        private void updatePreferredSize() {
            if (allRounds.isEmpty()) return;
            int numRounds = allRounds.size();
            int numFencersInFirstRound = allRounds.get(0).size() * 2;
            
            int panelWidth = (BOX_WIDTH + H_GAP) * (numRounds + 1);
            int panelHeight = numFencersInFirstRound * (BOX_HEIGHT + V_GAP);
            
            setPreferredSize(new Dimension(panelWidth, panelHeight));
            revalidate();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (allRounds.isEmpty()) return;

            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setStroke(new BasicStroke(1.5f));

            Map<String, Point> boxPositions = new HashMap<>();
            int yStep = BOX_HEIGHT + V_GAP;

            // === Disegna tutti i box e salva le posizioni ===
            for (int r = 0; r < allRounds.size(); r++) {
                int x = H_GAP + r * (BOX_WIDTH + H_GAP);
                List<String[]> currentRoundBouts = allRounds.get(r);
                
                int numSlotsInThisRound = (int) (allRounds.get(0).size() * 2 / Math.pow(2, r));

                for (int i = 0; i < numSlotsInThisRound; i++) {
                    String name = null;
                    int boutIndex = i / 2;
                    if (boutIndex < currentRoundBouts.size()) {
                        name = currentRoundBouts.get(boutIndex)[i % 2];
                    }
                    
                    int y;
                    if (r == 0) {
                        y = V_GAP + i * yStep;
                    } else {
                        Point prevP1 = boxPositions.get((r - 1) + "-" + (2 * i));
                        Point prevP2 = boxPositions.get((r - 1) + "-" + (2 * i + 1));
                        if(prevP1 == null || prevP2 == null) continue;
                        y = prevP1.y + (prevP2.y - prevP1.y) / 2;
                    }
                    
                    Point p = new Point(x, y);
                    boxPositions.put(r + "-" + i, p);

                    if (name != null) {
                         boolean isFinalWinner = (finalWinner != null && finalWinner.equals(name));
                         drawFencerBox(g2d, name, p.x, p.y, isFinalWinner);
                    }
                }
            }

            // === Disegna le linee di connessione ===
            for (int r = 1; r < allRounds.size(); r++) {
                int numSlotsInPrevRound = (int) (allRounds.get(0).size() * 2 / Math.pow(2, r - 1));

                for (int i = 0; i < numSlotsInPrevRound; i += 2) {
                    Point p1 = boxPositions.get((r - 1) + "-" + i);
                    Point p2 = boxPositions.get((r - 1) + "-" + (i + 1));
                    Point winnerPos = boxPositions.get(r + "-" + (i / 2));

                    if (p1 == null || p2 == null || winnerPos == null) continue;

                    int lineStartX = p1.x + BOX_WIDTH;
                    int lineEndX = winnerPos.x;
                    int connectorX = lineStartX + H_GAP / 2;
                    
                    // Controlla se il secondo schermidore nel turno precedente era un bye
                    String parent2Name = allRounds.get(r-1).get(i/2)[1];

                    if (parent2Name == null) {
                        g2d.drawLine(lineStartX, p1.y + BOX_HEIGHT / 2, lineEndX, winnerPos.y + BOX_HEIGHT / 2);
                    } else {
                        g2d.drawLine(lineStartX, p1.y + BOX_HEIGHT / 2, connectorX, p1.y + BOX_HEIGHT / 2);
                        g2d.drawLine(lineStartX, p2.y + BOX_HEIGHT / 2, connectorX, p2.y + BOX_HEIGHT / 2);
                        g2d.drawLine(connectorX, p1.y + BOX_HEIGHT / 2, connectorX, p2.y + BOX_HEIGHT / 2);
                        g2d.drawLine(connectorX, winnerPos.y + BOX_HEIGHT / 2, lineEndX, winnerPos.y + BOX_HEIGHT / 2);
                    }
                }
            }
        }
        
        private void drawFencerBox(Graphics2D g2d, String name, int x, int y, boolean isFinalWinner) {
            String text = (name != null) ? name : "BYE";
            Rectangle rect = new Rectangle(x, y, BOX_WIDTH, BOX_HEIGHT);
            
            if (isFinalWinner) {
                g2d.setColor(new Color(255, 215, 0)); // Oro
            } else {
                g2d.setColor(Color.WHITE);
            }
            g2d.fill(rect);
            g2d.setColor(Color.BLACK);
            g2d.draw(rect);
            g2d.setFont(new Font("Arial", Font.PLAIN, 11));
            drawCenteredString(g2d, text, rect);
        }

        private void drawCenteredString(Graphics g, String text, Rectangle rect) {
            FontMetrics metrics = g.getFontMetrics(g.getFont());
            int x_pos = rect.x + (rect.width - metrics.stringWidth(text)) / 2;
            int y_pos = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
            g.drawString(text, x_pos, y_pos);
        }
    }
}