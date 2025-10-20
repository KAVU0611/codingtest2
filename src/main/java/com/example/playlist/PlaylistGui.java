package com.example.playlist;

import com.example.playlist.model.SongEntry;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PlaylistGui {

    public static void launch(PlaylistService service, Path initialDir) {
        launch(service, initialDir, false);
    }

    public static void launch(PlaylistService service, Path initialDir, boolean initialRecursive) {
        SwingUtilities.invokeLater(() -> createAndShow(service, initialDir, initialRecursive));
    }

    private static void createAndShow(PlaylistService service, Path initialDir, boolean initialRecursive) {
        JFrame frame = new JFrame("Playlist Browser");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JTextField playlistDirField = new JTextField(initialDir != null ? initialDir.toString() : "playlist", 30);
        JButton browseBtn = new JButton("Browse...");
        JButton reloadBtn = new JButton("Reload");
        JButton addAlbumBtn = new JButton("Add Album...");

        JTextField albumField = new JTextField(12);
        JTextField artistField = new JTextField(12);
        JTextField titlePrefixField = new JTextField(12);
        JCheckBox sortByDuration = new JCheckBox("Sort by duration");
        JCheckBox recursiveBox = new JCheckBox("Recursive", initialRecursive);

        SongTableModel model = new SongTableModel();
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);

        JLabel statusLabel = new JLabel(" ");

        JPanel top = new JPanel();
        top.setLayout(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        gc.gridx = 0; gc.gridy = row; top.add(new JLabel("Playlist dir:"), gc);
        gc.gridx = 1; gc.gridy = row; gc.weightx = 1; top.add(playlistDirField, gc);
        gc.gridx = 2; gc.gridy = row; gc.weightx = 0; top.add(browseBtn, gc);
        gc.gridx = 3; gc.gridy = row; top.add(reloadBtn, gc);
        row++;
        gc.gridx = 0; gc.gridy = row; top.add(addAlbumBtn, gc);

        row++;
        gc.gridx = 0; gc.gridy = row; top.add(new JLabel("Album:"), gc);
        gc.gridx = 1; gc.gridy = row; top.add(albumField, gc);
        gc.gridx = 2; gc.gridy = row; top.add(new JLabel("Artist:"), gc);
        gc.gridx = 3; gc.gridy = row; top.add(artistField, gc);

        row++;
        gc.gridx = 0; gc.gridy = row; top.add(new JLabel("Title prefix:"), gc);
        gc.gridx = 1; gc.gridy = row; top.add(titlePrefixField, gc);
        gc.gridx = 2; gc.gridy = row; top.add(sortByDuration, gc);
        gc.gridx = 3; gc.gridy = row; top.add(recursiveBox, gc);

        frame.getContentPane().add(top, BorderLayout.NORTH);
        frame.getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
        frame.getContentPane().add(statusLabel, BorderLayout.SOUTH);

        Action reloadAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Path dir = service.resolvePlaylistDir(playlistDirField.getText());
                try {
                    if (!Files.exists(dir)) {
                        statusLabel.setText("Directory not found: " + dir);
                        model.setSongs(List.of());
                        return;
                    }
                    List<SongEntry> songs = service.loadSongs(dir, recursiveBox.isSelected());
                    songs = service.filterSongs(
                            songs,
                            albumField.getText(),
                            artistField.getText(),
                            titlePrefixField.getText());
                    songs = service.sortSongs(songs, sortByDuration.isSelected());
                    model.setSongs(songs);
                    statusLabel.setText("Total songs: " + songs.size());
                } catch (Exception ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    model.setSongs(List.of());
                }
            }
        };

        reloadBtn.addActionListener(reloadAction);
        browseBtn.addActionListener(ev -> {
            JFileChooser chooser = new JFileChooser(playlistDirField.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                playlistDirField.setText(chooser.getSelectedFile().toPath().toString());
                reloadAction.actionPerformed(null);
            }
        });

        // Add album action
        addAlbumBtn.addActionListener(ev -> {
            showAddAlbumDialog(frame, service, playlistDirField, statusLabel, model, reloadAction);
        });

        // Initial load
        reloadAction.actionPerformed(null);

        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void showAddAlbumDialog(JFrame parent, PlaylistService service, JTextField playlistDirField,
                                           JLabel statusLabel, SongTableModel model, Action reloadAction) {
        JDialog dialog = new JDialog(parent, "Add Album", true);
        JTextField nameField = new JTextField(30);
        JTextArea songsArea = new JTextArea(12, 60);
        songsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JLabel hint = new JLabel("Enter one song per line as: Title\tArtist\tmm:ss or hh:mm:ss");

        JButton saveBtn = new JButton("Save");
        JButton cancelBtn = new JButton("Cancel");

        JPanel panel = new JPanel(new BorderLayout(8,8));
        JPanel north = new JPanel(new BorderLayout(4,4));
        north.add(new JLabel("Album name:"), BorderLayout.WEST);
        north.add(nameField, BorderLayout.CENTER);
        panel.add(north, BorderLayout.NORTH);
        panel.add(hint, BorderLayout.CENTER);
        panel.add(new JScrollPane(songsArea), BorderLayout.SOUTH);

        JPanel buttons = new JPanel();
        buttons.add(saveBtn);
        buttons.add(cancelBtn);

        dialog.getContentPane().add(panel, BorderLayout.CENTER);
        dialog.getContentPane().add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);

        saveBtn.addActionListener(e -> {
            String albumName = nameField.getText().trim();
            if (albumName.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Album name is required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String text = songsArea.getText();
            String[] lines = text.split("\r?\n");
            java.util.List<String> out = new java.util.ArrayList<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                String[] parts = trimmed.split("\t");
                if (parts.length < 3) {
                    JOptionPane.showMessageDialog(dialog, "Invalid line (need 3 tab-separated columns):\n" + line, "Validation", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                String duration = parts[2].trim();
                if (!service.isParsableDuration(duration)) {
                    JOptionPane.showMessageDialog(dialog, "Invalid duration: " + duration, "Validation", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                out.add(String.join("\t", parts[0].trim(), parts[1].trim(), duration));
            }
            if (out.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please enter at least one song.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                java.nio.file.Path dir = service.resolvePlaylistDir(playlistDirField.getText());
                java.nio.file.Path file = service.writeAlbum(dir, albumName, out);
                JOptionPane.showMessageDialog(dialog, "Album saved: " + file.getFileName(), "Saved", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
                reloadAction.actionPerformed(null);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Failed to save album: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.setVisible(true);
    }

    private static class SongTableModel extends AbstractTableModel {
        private List<SongEntry> songs = List.of();
        private final String[] columns = {"Album", "#", "Title", "Artist", "Duration"};

        public void setSongs(List<SongEntry> songs) {
            this.songs = songs;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return songs.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SongEntry s = songs.get(rowIndex);
            switch (columnIndex) {
                case 0: return s.getAlbumName();
                case 1: return s.getTrackNumber();
                case 2: return s.getTitle();
                case 3: return s.getArtist();
                case 4: return s.getDuration();
                default: return "";
            }
        }
    }
}
