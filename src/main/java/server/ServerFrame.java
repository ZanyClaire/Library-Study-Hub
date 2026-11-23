package server;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import common.AppConfig;

/**
 * Desktop Librarian Server Console window.
 * Includes live student conversation monitor + Librarian announcement bar,
 * Server Activity Log tab, live Student Monitor table (Student | Seat | Study Timer),
 * and Study Seats — Librarian Approval controls.
 * Designed with the NetBeans GUI Builder (ServerFrame.form).
 */
public final class ServerFrame extends javax.swing.JFrame implements ServerListener {

    private static final long serialVersionUID = 1L;
    private final DefaultTableModel studentsModel = new DefaultTableModel(
            new Object[]{"Student", "Seat", "Study Timer"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DefaultListModel<String> seatsModel = new DefaultListModel<>();
    private Seat.Status[] seatStatuses = new Seat.Status[0];
    private transient volatile ChatServer server;

    public ServerFrame() {
        initComponents();
        applyTheme();
        // defaults come from config.properties, not from the code
        setSize(new Dimension(AppConfig.serverWidth(), AppConfig.serverHeight()));
        setLocationRelativeTo(null);
        portField.setText(String.valueOf(AppConfig.serverPort()));
        seatsSpinner.setModel(new SpinnerNumberModel(
                Math.min(AppConfig.defaultSeats(), AppConfig.maxSeats()), 1, AppConfig.maxSeats(), 1));
        studentsTable.getTableHeader().setReorderingAllowed(false);
        studentsTable.setFillsViewportHeight(true);
        studentsTable.getColumnModel().getColumn(0).setPreferredWidth(95);
        studentsTable.getColumnModel().getColumn(1).setPreferredWidth(125);
        studentsTable.getColumnModel().getColumn(2).setPreferredWidth(190);
        appendChatInfo("Live student conversations will appear here once the server starts.");
        log("Ready. LAN address(es) of this computer: " + String.join(", ", localAddresses()));
    }

    private void applyTheme() {
        Color headerBg = AppConfig.color("theme.headerBg", "#162A45");
        Color headerFg = AppConfig.color("theme.headerFg", "#F8FAFC");
        Color headerSubFg = AppConfig.color("theme.headerSubFg", "#CBDDF5");
        Color paper = AppConfig.color("theme.cardBg", "#F9FBFD");
        Color altRow = AppConfig.color("theme.altRowBg", "#F1F6FC");
        Color surface = AppConfig.color("theme.surfaceBg", "#EBF1F8");
        Color border = AppConfig.color("theme.borderColor", "#B9CBE0");
        Color primary = AppConfig.color("theme.primaryBtnBg", "#2B6CB0");
        Color primaryFg = AppConfig.color("theme.primaryBtnFg", "#F8FAFC");
        Color secondary = AppConfig.color("theme.secondaryBtnBg", "#DDE7F3");
        Color navy = AppConfig.color("theme.navyText", "#1B365D");
        Color body = AppConfig.color("theme.bodyText", "#24374F");
        Color selection = AppConfig.color("theme.selectionBg", "#D4E4F7");

        getContentPane().setBackground(surface);
        headerPanel.setBackground(headerBg);
        controlPanel.setBackground(headerBg);
        brandLabel.setForeground(headerFg);
        portLabel.setForeground(headerFg);
        seatsLabel.setForeground(headerFg);
        statusLabel.setForeground(headerSubFg);
        AppConfig.bindPrimaryButton(startButton);
        stopButton.setBackground(secondary);
        stopButton.setForeground(navy);

        centerTabs.setBackground(surface);
        centerTabs.setForeground(navy);
        chatMonitorPanel.setBackground(surface);
        chatMonitorScroll.setBorder(BorderFactory.createLineBorder(border, 1));
        chatMonitorPane.setBackground(paper);
        chatMonitorPane.setForeground(body);
        chatMonitorPane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        announcementPanel.setBackground(headerBg);
        announcementLabel.setForeground(headerFg);
        announcementField.setBackground(paper);
        announcementField.setForeground(body);
        AppConfig.bindPrimaryButton(announceButton);
        logScroll.setBackground(surface);
        logScroll.setBorder(BorderFactory.createLineBorder(border, 1));
        logArea.setBackground(paper);
        logArea.setForeground(body);
        logArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        rightSidePanel.setBackground(surface);
        studentsScroll.setBackground(surface);
        studentsScroll.setBorder(AppConfig.titledBorder("Student Monitor (0)"));
        studentsScroll.getViewport().setBackground(paper);
        studentsTable.setBackground(paper);
        studentsTable.setForeground(body);
        studentsTable.setGridColor(secondary);
        studentsTable.setSelectionBackground(selection);
        studentsTable.setSelectionForeground(navy);
        JTableHeader th = studentsTable.getTableHeader();
        Color thBg = AppConfig.color("theme.tableHeaderBg", "#1E3A5F");
        Color thFg = AppConfig.color("theme.tableHeaderFg", "#F8FAFC");
        th.setDefaultRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                lbl.setOpaque(true);
                lbl.setBackground(thBg);
                lbl.setForeground(thFg);
                lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
                lbl.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 1, border),
                        BorderFactory.createEmptyBorder(4, 6, 4, 6)));
                return lbl;
            }
        });
        studentsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? paper : altRow);
                    c.setForeground(column == 0 ? navy : body);
                }
                if (c instanceof JLabel lbl) {
                    lbl.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
                }
                return c;
            }
        });

        librarianSeatsPanel.setBackground(surface);
        librarianSeatsPanel.setBorder(AppConfig.titledBorder("Study Seats — Librarian Approval"));
        librarianButtonsPanel.setBackground(surface);
        seatHintLabel.setForeground(AppConfig.color("theme.mutedText", "#64748B"));
        seatsScroll.setBorder(BorderFactory.createLineBorder(border, 1));
        seatsList.setBackground(paper);
        seatsList.setForeground(body);
        seatsList.setFixedCellHeight(28);
        seatsList.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                lbl.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
                if (isSelected) {
                    lbl.setBackground(primary);
                    lbl.setForeground(primaryFg);
                    lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
                } else if (index >= 0 && index < seatStatuses.length) {
                    switch (seatStatuses[index]) {
                        case PENDING -> {
                            lbl.setBackground(AppConfig.color("theme.seatPendingBg", "#FEF6E4"));
                            lbl.setForeground(AppConfig.color("theme.seatPendingFg", "#8A5300"));
                            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
                        }
                        case TAKEN -> {
                            lbl.setBackground(AppConfig.color("theme.seatTakenBg", "#DCE5F2"));
                            lbl.setForeground(AppConfig.color("theme.seatTakenFg", "#2D4363"));
                            lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN));
                        }
                        default -> {
                            lbl.setBackground(AppConfig.color("theme.seatFreeBg", "#ECF6F1"));
                            lbl.setForeground(AppConfig.color("theme.seatFreeFg", "#1E5641"));
                            lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN));
                        }
                    }
                }
                return lbl;
            }
        });
        AppConfig.bindPrimaryButton(approveSeatButton);
        denySeatButton.setBackground(secondary);
        denySeatButton.setForeground(navy);
        releaseSeatButton.setBackground(paper);
        releaseSeatButton.setForeground(navy);
    }

    /** This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        headerPanel = new javax.swing.JPanel();
        brandLabel = new javax.swing.JLabel();
        controlPanel = new javax.swing.JPanel();
        portLabel = new javax.swing.JLabel();
        portField = new javax.swing.JTextField();
        seatsLabel = new javax.swing.JLabel();
        seatsSpinner = new javax.swing.JSpinner();
        startButton = new javax.swing.JButton();
        stopButton = new javax.swing.JButton();
        statusLabel = new javax.swing.JLabel();
        centerTabs = new javax.swing.JTabbedPane();
        chatMonitorPanel = new javax.swing.JPanel();
        chatMonitorScroll = new javax.swing.JScrollPane();
        chatMonitorPane = new javax.swing.JTextPane();
        announcementPanel = new javax.swing.JPanel();
        announcementLabel = new javax.swing.JLabel();
        announcementField = new javax.swing.JTextField();
        announceButton = new javax.swing.JButton();
        logScroll = new javax.swing.JScrollPane();
        logArea = new javax.swing.JTextArea();
        rightSidePanel = new javax.swing.JPanel();
        studentsScroll = new javax.swing.JScrollPane();
        studentsTable = new javax.swing.JTable();
        librarianSeatsPanel = new javax.swing.JPanel();
        seatHintLabel = new javax.swing.JLabel();
        seatsScroll = new javax.swing.JScrollPane();
        seatsList = new javax.swing.JList<>();
        librarianButtonsPanel = new javax.swing.JPanel();
        approveSeatButton = new javax.swing.JButton();
        denySeatButton = new javax.swing.JButton();
        releaseSeatButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Library Study Hub — Librarian Server Console");
        setBackground(new java.awt.Color(244, 247, 252));
        setMinimumSize(new java.awt.Dimension(1060, 620));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        getContentPane().setLayout(new java.awt.BorderLayout(10, 10));

        headerPanel.setBackground(new java.awt.Color(15, 37, 71));
        headerPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 16, 10, 16));
        headerPanel.setLayout(new java.awt.BorderLayout(0, 4));

        brandLabel.setFont(new java.awt.Font("SansSerif", 1, 16)); // NOI18N
        brandLabel.setForeground(new java.awt.Color(255, 255, 255));
        brandLabel.setText("LIBRARY STUDY HUB  |  Librarian Server Console");
        headerPanel.add(brandLabel, java.awt.BorderLayout.PAGE_START);

        controlPanel.setBackground(new java.awt.Color(15, 37, 71));
        controlPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 5));

        portLabel.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        portLabel.setForeground(new java.awt.Color(255, 255, 255));
        portLabel.setText("Port:");
        controlPanel.add(portLabel);

        portField.setColumns(6);
        controlPanel.add(portField);

        seatsLabel.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        seatsLabel.setForeground(new java.awt.Color(255, 255, 255));
        seatsLabel.setText("Study seats:");
        controlPanel.add(seatsLabel);
        controlPanel.add(seatsSpinner);

        startButton.setBackground(new java.awt.Color(29, 95, 214));
        startButton.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        startButton.setForeground(new java.awt.Color(255, 255, 255));
        startButton.setText("Start Server");
        startButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startButtonActionPerformed(evt);
            }
        });
        controlPanel.add(startButton);

        stopButton.setBackground(new java.awt.Color(226, 232, 240));
        stopButton.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        stopButton.setForeground(new java.awt.Color(15, 37, 71));
        stopButton.setText("Stop Server");
        stopButton.setEnabled(false);
        stopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopButtonActionPerformed(evt);
            }
        });
        controlPanel.add(stopButton);

        statusLabel.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        statusLabel.setForeground(new java.awt.Color(191, 219, 254));
        statusLabel.setText("Stopped");
        controlPanel.add(statusLabel);

        headerPanel.add(controlPanel, java.awt.BorderLayout.PAGE_END);

        getContentPane().add(headerPanel, java.awt.BorderLayout.PAGE_START);

        centerTabs.setBackground(new java.awt.Color(244, 247, 252));
        centerTabs.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        centerTabs.setForeground(new java.awt.Color(15, 37, 71));
        centerTabs.setPreferredSize(new java.awt.Dimension(640, 500));

        chatMonitorPanel.setBackground(new java.awt.Color(244, 247, 252));
        chatMonitorPanel.setLayout(new java.awt.BorderLayout(0, 6));

        chatMonitorPane.setEditable(false);
        chatMonitorPane.setBackground(new java.awt.Color(255, 255, 255));
        chatMonitorPane.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        chatMonitorScroll.setViewportView(chatMonitorPane);

        chatMonitorPanel.add(chatMonitorScroll, java.awt.BorderLayout.CENTER);

        announcementPanel.setBackground(new java.awt.Color(15, 37, 71));
        announcementPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 10, 8, 10));
        announcementPanel.setLayout(new java.awt.BorderLayout(8, 0));

        announcementLabel.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        announcementLabel.setForeground(new java.awt.Color(255, 255, 255));
        announcementLabel.setText("Announcement:");
        announcementPanel.add(announcementLabel, java.awt.BorderLayout.LINE_START);

        announcementField.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        announcementField.setEnabled(false);
        announcementField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                announcementFieldActionPerformed(evt);
            }
        });
        announcementPanel.add(announcementField, java.awt.BorderLayout.CENTER);

        announceButton.setBackground(new java.awt.Color(29, 95, 214));
        announceButton.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        announceButton.setForeground(new java.awt.Color(255, 255, 255));
        announceButton.setText("Send to All");
        announceButton.setEnabled(false);
        announceButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                announceButtonActionPerformed(evt);
            }
        });
        announcementPanel.add(announceButton, java.awt.BorderLayout.LINE_END);

        chatMonitorPanel.add(announcementPanel, java.awt.BorderLayout.PAGE_END);

        centerTabs.addTab("Live Student Chat", chatMonitorPanel);

        logScroll.setBackground(new java.awt.Color(244, 247, 252));

        logArea.setEditable(false);
        logArea.setBackground(new java.awt.Color(255, 255, 255));
        logArea.setColumns(20);
        logArea.setFont(new java.awt.Font("Monospaced", 0, 13)); // NOI18N
        logArea.setForeground(new java.awt.Color(15, 37, 71));
        logArea.setLineWrap(true);
        logArea.setRows(5);
        logArea.setWrapStyleWord(true);
        logScroll.setViewportView(logArea);

        centerTabs.addTab("Server Activity Log", logScroll);

        getContentPane().add(centerTabs, java.awt.BorderLayout.CENTER);

        rightSidePanel.setBackground(new java.awt.Color(244, 247, 252));
        rightSidePanel.setPreferredSize(new java.awt.Dimension(440, 500));
        rightSidePanel.setLayout(new java.awt.BorderLayout(0, 8));

        studentsScroll.setBackground(new java.awt.Color(244, 247, 252));
        studentsScroll.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Student Monitor (0)", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("SansSerif", 1, 12), new java.awt.Color(15, 37, 71))); // NOI18N
        studentsScroll.setPreferredSize(new java.awt.Dimension(440, 225));

        studentsTable.setFont(new java.awt.Font("SansSerif", 0, 12)); // NOI18N
        studentsTable.setForeground(new java.awt.Color(15, 37, 71));
        studentsTable.setModel(studentsModel);
        studentsTable.setRowHeight(24);
        studentsScroll.setViewportView(studentsTable);

        rightSidePanel.add(studentsScroll, java.awt.BorderLayout.PAGE_START);

        librarianSeatsPanel.setBackground(new java.awt.Color(244, 247, 252));
        librarianSeatsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Study Seats — Librarian Approval", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("SansSerif", 1, 12), new java.awt.Color(15, 37, 71))); // NOI18N
        librarianSeatsPanel.setLayout(new java.awt.BorderLayout(0, 6));

        seatHintLabel.setFont(new java.awt.Font("SansSerif", 0, 12)); // NOI18N
        seatHintLabel.setForeground(new java.awt.Color(15, 37, 71));
        seatHintLabel.setText("Select a seat request to Approve, Deny, or Release.");
        librarianSeatsPanel.add(seatHintLabel, java.awt.BorderLayout.PAGE_START);

        seatsList.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        seatsList.setForeground(new java.awt.Color(15, 37, 71));
        seatsList.setModel(seatsModel);
        seatsList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        seatsList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                seatsListValueChanged(evt);
            }
        });
        seatsScroll.setViewportView(seatsList);

        librarianSeatsPanel.add(seatsScroll, java.awt.BorderLayout.CENTER);

        librarianButtonsPanel.setBackground(new java.awt.Color(244, 247, 252));
        librarianButtonsPanel.setLayout(new java.awt.GridLayout(1, 3, 6, 0));

        approveSeatButton.setBackground(new java.awt.Color(29, 95, 214));
        approveSeatButton.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        approveSeatButton.setForeground(new java.awt.Color(255, 255, 255));
        approveSeatButton.setText("Approve");
        approveSeatButton.setEnabled(false);
        approveSeatButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                approveSeatButtonActionPerformed(evt);
            }
        });
        librarianButtonsPanel.add(approveSeatButton);

        denySeatButton.setBackground(new java.awt.Color(226, 232, 240));
        denySeatButton.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        denySeatButton.setForeground(new java.awt.Color(15, 37, 71));
        denySeatButton.setText("Deny");
        denySeatButton.setEnabled(false);
        denySeatButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                denySeatButtonActionPerformed(evt);
            }
        });
        librarianButtonsPanel.add(denySeatButton);

        releaseSeatButton.setForeground(new java.awt.Color(15, 37, 71));
        releaseSeatButton.setText("Release");
        releaseSeatButton.setEnabled(false);
        releaseSeatButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                releaseSeatButtonActionPerformed(evt);
            }
        });
        librarianButtonsPanel.add(releaseSeatButton);

        librarianSeatsPanel.add(librarianButtonsPanel, java.awt.BorderLayout.PAGE_END);

        rightSidePanel.add(librarianSeatsPanel, java.awt.BorderLayout.CENTER);

        getContentPane().add(rightSidePanel, java.awt.BorderLayout.LINE_END);

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void startButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startButtonActionPerformed
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port must be a number from 1 to 65535.",
                    "Invalid port", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int seats = (Integer) seatsSpinner.getValue();
        setRunningUi(true);
        statusLabel.setText("Starting...");
        // opening the port is done off the GUI thread
        new SwingWorker<Void, Void>() {
            private IOException failure;

            @Override
            protected Void doInBackground() {
                ChatServer s = new ChatServer(port, seats, ServerFrame.this);
                try {
                    s.start();
                    server = s;
                } catch (IOException e) {
                    failure = e;
                }
                return null;
            }

            @Override
            protected void done() {
                if (failure != null) {
                    setRunningUi(false);
                    log("Could not start: " + failure.getMessage());
                    JOptionPane.showMessageDialog(ServerFrame.this,
                            "Could not start the server on port " + port + ":\n" + failure.getMessage()
                            + "\nThe port may already be in use.", "Server error", JOptionPane.ERROR_MESSAGE);
                } else {
                    statusLabel.setText("Running on port " + port);
                    announcementField.setEnabled(true);
                    announceButton.setEnabled(true);
                }
            }
        }.execute();
    }//GEN-LAST:event_startButtonActionPerformed

    private void stopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButtonActionPerformed
        stopServer();
    }//GEN-LAST:event_stopButtonActionPerformed

    private void announcementFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_announcementFieldActionPerformed
        sendAnnouncement();
    }//GEN-LAST:event_announcementFieldActionPerformed

    private void announceButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_announceButtonActionPerformed
        sendAnnouncement();
    }//GEN-LAST:event_announceButtonActionPerformed

    private void seatsListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_seatsListValueChanged
        if (!evt.getValueIsAdjusting()) {
            updateSeatButtons();
        }
    }//GEN-LAST:event_seatsListValueChanged

    private void approveSeatButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_approveSeatButtonActionPerformed
        runLibrarianAction("approve");
    }//GEN-LAST:event_approveSeatButtonActionPerformed

    private void denySeatButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_denySeatButtonActionPerformed
        runLibrarianAction("deny");
    }//GEN-LAST:event_denySeatButtonActionPerformed

    private void releaseSeatButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_releaseSeatButtonActionPerformed
        runLibrarianAction("release");
    }//GEN-LAST:event_releaseSeatButtonActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        if (server != null && server.isRunning()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Stop the server and disconnect all users?", "Exit", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
            server.stop();
        }
        dispose();
        System.exit(0);
    }//GEN-LAST:event_formWindowClosing

    private void sendAnnouncement() {
        ChatServer s = server;
        if (s == null || !s.isRunning()) {
            return;
        }
        String text = announcementField.getText();
        String error = s.sendAnnouncement(text);
        if (error != null) {
            JOptionPane.showMessageDialog(this, error, "Announcement", JOptionPane.WARNING_MESSAGE);
            return;
        }
        announcementField.setText("");
    }

    private void runLibrarianAction(String action) {
        ChatServer s = server;
        int idx = seatsList.getSelectedIndex();
        if (s == null || idx < 0) {
            return;
        }
        int seatNumber = idx + 1;
        String error = switch (action) {
            case "approve" -> s.approveSeat(seatNumber);
            case "deny" -> s.denySeat(seatNumber);
            case "release" -> s.releaseSeatByLibrarian(seatNumber);
            default -> null;
        };
        if (error != null) {
            JOptionPane.showMessageDialog(this, error, "Seat action", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void updateSeatButtons() {
        int idx = seatsList.getSelectedIndex();
        boolean valid = server != null && idx >= 0 && idx < seatStatuses.length;
        Seat.Status st = valid ? seatStatuses[idx] : Seat.Status.FREE;
        approveSeatButton.setEnabled(valid && st == Seat.Status.PENDING);
        denySeatButton.setEnabled(valid && st == Seat.Status.PENDING);
        releaseSeatButton.setEnabled(valid && st != Seat.Status.FREE);
    }

    private void stopServer() {
        if (server != null) {
            ChatServer s = server;
            server = null;
            new Thread(s::stop, "server-stop").start();
        }
    }

    private void setRunningUi(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        portField.setEnabled(!running);
        seatsSpinner.setEnabled(!running);
        if (!running) {
            statusLabel.setText("Stopped");
            announcementField.setEnabled(false);
            announceButton.setEnabled(false);
            updateSeatButtons();
        }
    }

    private void appendChatInfo(String text) {
        appendStyled("* " + text + "\n", AppConfig.color("theme.mutedText", "#475569"), false, true);
    }

    private void appendStyled(String text, Color color, boolean bold, boolean italic) {
        StyledDocument doc = chatMonitorPane.getStyledDocument();
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, color);
        StyleConstants.setBold(a, bold);
        StyleConstants.setItalic(a, italic);
        try {
            doc.insertString(doc.getLength(), text, a);
        } catch (BadLocationException ignored) {
            // inserting at end cannot fail
        }
        chatMonitorPane.setCaretPosition(doc.getLength());
    }

    // ---------------- ServerListener (called from server threads) ----------------

    @Override
    public void log(String text) {
        String line = "[" + java.time.LocalTime.now().withNano(0) + "] " + text + "\n";
        SwingUtilities.invokeLater(() -> {
            logArea.append(line);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public void chatMessage(String time, String sender, String text) {
        SwingUtilities.invokeLater(() -> {
            boolean isLibrarian = "Librarian".equals(sender);
            appendStyled("[" + time + "] ", AppConfig.color("theme.mutedText", "#64748B"), false, false);
            appendStyled(isLibrarian ? "[Librarian] " : sender + ": ",
                    isLibrarian ? AppConfig.color("theme.accentBlue", "#2B6CB0")
                                : AppConfig.color("theme.navyText", "#1B365D"), true, false);
            appendStyled(text + "\n", AppConfig.color("theme.bodyText", "#24374F"), isLibrarian, false);
        });
    }

    @Override
    public void studentsChanged(List<String[]> rows) {
        List<String[]> copy = new ArrayList<>(rows.size());
        for (String[] r : rows) {
            copy.add(r.clone());
        }
        SwingUtilities.invokeLater(() -> {
            studentsModel.setRowCount(0);
            for (String[] r : copy) {
                studentsModel.addRow(r);
            }
            ((javax.swing.border.TitledBorder) studentsScroll.getBorder())
                    .setTitle("Student Monitor (" + copy.size() + ")");
            studentsScroll.repaint();
        });
    }

    @Override
    public void seatsChanged(Seat.Status[] statuses, String[] holders) {
        Seat.Status[] stCopy = statuses.clone();
        String[] hCopy = holders.clone();
        SwingUtilities.invokeLater(() -> {
            int prev = seatsList.getSelectedIndex();
            seatStatuses = stCopy;
            seatsModel.clear();
            int pendingCount = 0;
            for (int i = 0; i < stCopy.length; i++) {
                String line = switch (stCopy[i]) {
                    case PENDING -> {
                        pendingCount++;
                        yield "Seat " + (i + 1) + "  —  PENDING: " + hCopy[i];
                    }
                    case TAKEN -> "Seat " + (i + 1) + "  —  Taken: " + hCopy[i];
                    default -> "Seat " + (i + 1) + "  —  Free";
                };
                seatsModel.addElement(line);
            }
            if (prev >= 0 && prev < stCopy.length) {
                seatsList.setSelectedIndex(prev);
            }
            ((javax.swing.border.TitledBorder) librarianSeatsPanel.getBorder())
                    .setTitle(pendingCount > 0
                            ? "Study Seats — " + pendingCount + " Pending Request(s)"
                            : "Study Seats — Librarian Approval");
            librarianSeatsPanel.repaint();
            updateSeatButtons();
        });
    }

    @Override
    public void serverStopped() {
        SwingUtilities.invokeLater(() -> setRunningUi(false));
    }

    private static List<String> localAddresses() {
        List<String> result = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address) {
                        result.add(a.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignored) {
            // no interfaces
        }
        if (result.isEmpty()) {
            result.add("127.0.0.1");
        }
        return result;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        AppConfig.applyLookAndFeel();

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> new ServerFrame().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton announceButton;
    private javax.swing.JTextField announcementField;
    private javax.swing.JLabel announcementLabel;
    private javax.swing.JPanel announcementPanel;
    private javax.swing.JButton approveSeatButton;
    private javax.swing.JLabel brandLabel;
    private javax.swing.JTabbedPane centerTabs;
    private javax.swing.JTextPane chatMonitorPane;
    private javax.swing.JPanel chatMonitorPanel;
    private javax.swing.JScrollPane chatMonitorScroll;
    private javax.swing.JPanel controlPanel;
    private javax.swing.JButton denySeatButton;
    private javax.swing.JPanel headerPanel;
    private javax.swing.JPanel librarianButtonsPanel;
    private javax.swing.JPanel librarianSeatsPanel;
    private javax.swing.JTextArea logArea;
    private javax.swing.JScrollPane logScroll;
    private javax.swing.JTextField portField;
    private javax.swing.JLabel portLabel;
    private javax.swing.JButton releaseSeatButton;
    private javax.swing.JPanel rightSidePanel;
    private javax.swing.JLabel seatHintLabel;
    private javax.swing.JLabel seatsLabel;
    private javax.swing.JList<String> seatsList;
    private javax.swing.JScrollPane seatsScroll;
    private javax.swing.JSpinner seatsSpinner;
    private javax.swing.JButton startButton;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JButton stopButton;
    private javax.swing.JScrollPane studentsScroll;
    private javax.swing.JTable studentsTable;
    // End of variables declaration//GEN-END:variables
}
