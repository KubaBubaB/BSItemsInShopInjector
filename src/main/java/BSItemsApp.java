import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.codehaus.jettison.json.*;

public class BSItemsApp extends JFrame {
    private JButton openButton, confirmButton;
    private JFileChooser fileChooser;
    private JTextArea filesTextArea;
    private JTextField valueTextField;
    private JRadioButton[] tierRadioButtons;
    private List<File> selectedFiles = new ArrayList<>();
    private File dir;

    public BSItemsApp() {
        setTitle("Blade & Sorcery Shop Injector");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        createComponents();
        layoutComponents();
        setVisible(true);
    }

    private void createComponents() {
        openButton = new JButton("Select JSON Files with items you want to add to the shop");
        confirmButton = new JButton("Run");
        filesTextArea = new JTextArea(10, 30);
        filesTextArea.setText("Selected files will appear here\n");
        valueTextField = new JTextField("0", 10);

        tierRadioButtons = new JRadioButton[5];
        ButtonGroup tierGroup = new ButtonGroup();
        for(int i = 0; i < 5; i++) {
            tierRadioButtons[i] = new JRadioButton("T"+ i);
            tierGroup.add(tierRadioButtons[i]);
        }
        tierRadioButtons[0].setSelected(true);

        openButton.addActionListener(this::openFiles);
        confirmButton.addActionListener(this::processFiles);
    }

    private void layoutComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Radio button panel
        JPanel radioPanel = new JPanel(new GridLayout(5, 1));
        radioPanel.setPreferredSize(new Dimension(200, 100));
        radioPanel.setBorder(BorderFactory.createTitledBorder("Select a tier:"));
        for(JRadioButton rb : tierRadioButtons) {
            radioPanel.add(rb);
        }

        // Center area with scrollable text area
        JScrollPane scrollPane = new JScrollPane(filesTextArea);
        filesTextArea.setEditable(false);

        // Left panel
        JPanel leftPanel = new JPanel(new BorderLayout());
        //tier
        leftPanel.add(radioPanel, BorderLayout.NORTH);
        //cost
        JPanel costPanel = new JPanel(new GridLayout(7, 1));
        costPanel.add(new JLabel("Cost of the item/s in the shop:"));
        costPanel.add(valueTextField);
        leftPanel.add(costPanel, BorderLayout.CENTER);
        // Right panel
        JPanel rightPanel = new JPanel(new BorderLayout());
        // Selected files
        rightPanel.add(scrollPane, BorderLayout.NORTH);
        // Select files button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(openButton);
        rightPanel.add(buttonPanel, BorderLayout.CENTER);
        // Bottom panel
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        // Confirm button
        bottomPanel.add(confirmButton);

        // Assemble main panel
        mainPanel.add(leftPanel, BorderLayout.WEST);

        mainPanel.add(rightPanel, BorderLayout.EAST);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void openFiles(ActionEvent e) {
        fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        int returnValue = fileChooser.showOpenDialog(this);

        if(returnValue == JFileChooser.APPROVE_OPTION) {
            selectedFiles.clear();
            filesTextArea.setText("");

            for(File file : fileChooser.getSelectedFiles()) {
                selectedFiles.add(file);
                filesTextArea.append(file.getName() + "\n");
            }

            if(!selectedFiles.isEmpty()) {
                dir = selectedFiles.get(0).getParentFile();
            }
        }
    }

    private void processFiles(ActionEvent e) {
        if(selectedFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No files selected!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int newValue = 0;
        try {
            newValue = Integer.parseInt(valueTextField.getText());
        } catch(NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid value! Please enter a number.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int selectedTier = getSelectedTier();
        List<String> reportLines = new ArrayList<>();

        for(File file : selectedFiles) {
            if(processSingleFile(file, newValue, selectedTier, reportLines)) {
                filesTextArea.append("Processed: " + file.getName() + "\n");
            }
        }

        createSummaryFile(reportLines, selectedTier);
    }

    private int getSelectedTier() {
        for(int i = 0; i < tierRadioButtons.length; i++) {
            if(tierRadioButtons[i].isSelected()) return i;
        }
        return 0;
    }

    private boolean processSingleFile(File file, int newValue, int tier, List<String> reportLines) {
        String newFileName = file.getName();
        String id = "";
        try (FileReader reader = new FileReader(file)) {
            // Read the file content into a StringBuilder
            StringBuilder content = new StringBuilder();
            int character;
            while ((character = reader.read()) != -1) {
                content.append((char) character);
            }

            // Parse the JSON content using Jettison
            JSONObject json = new JSONObject(content.toString());

            // Update value and tier in the JSON
            if (json.has("value")) {
                json.put("value", newValue);
            }
            if (json.has("tier")) {
                json.put("tier", tier);
            }
            if(json.has("id")){
                id = json.getString("id");
            }

            // Save modified file
            File newFile = new File(file.getParent(), newFileName);

            if(file.delete()){
                JOptionPane.showMessageDialog(this,
                        "Error deleting old item file " + file.getName(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }
            else{
                try (FileWriter writer = new FileWriter(newFile)) {
                    writer.write(json.toString(2));
                }
                // Add to report
                reportLines.add("File: " + id +
                        " | New Value: " + newValue +
                        " | Tier: T" + tier);
                return true;
            }
        } catch (IOException | JSONException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error processing " + file.getName() + ": " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void createSummaryFile(List<String> reportLines, int tier) {
        try {
            JSONObject summaryJson = new JSONObject();

            // Add metadata
            summaryJson.put("$type", "ThunderRoad.LootTable, ThunderRoad");
            summaryJson.put("id", "Shop_WeaponRack_T%s".formatted(tier));

            summaryJson.put("sensitiveContent", "None");
            summaryJson.put("sensitiveFilterBehaviour", "Discard");
            summaryJson.put("version", 1);

            // Create array for modified files
            JSONArray processedFiles = new JSONArray();

            JSONObject fileEntry = new JSONObject();
            fileEntry.put("$type", "ThunderRoad.LootTable+DropLevel, ThunderRoad");
            fileEntry.put("dropLevel", 0);

            JSONArray drops = new JSONArray();

            for(String line : reportLines) {
                try {
                    String[] parts = line.split(" \\| ");
                    if(parts.length != 3) continue;
                    String[] fileParts = parts[0].split(": ");
                    JSONObject dropEntry = new JSONObject();
                    dropEntry.put("$type", "ThunderRoad.LootTable+Drop, ThunderRoad");

                    dropEntry.put("referenceID", fileParts[1].trim());

                    dropEntry.put("reference", "Item");

                    dropEntry.put("randMode", "ItemCount");

                    JSONObject minMaxRange = new JSONObject();
                    minMaxRange.put("x", "1.0");
                    minMaxRange.put("y", "1.0");
                    dropEntry.put("minMaxRand", minMaxRange);

                    dropEntry.put("probabilityWeight", "100.0");

                    drops.put(dropEntry);
                } catch(Exception e) {
                    JOptionPane.showMessageDialog(this,
                            "Error creating summary file: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }

            fileEntry.put("drops", drops);
            processedFiles.put(fileEntry);

            summaryJson.put("levelledDrops", processedFiles);
            summaryJson.put("groupPath", "Shop");


            // Write JSON to file with pretty print
            File parentDir = dir;
            boolean found = false;
            outerLoop:
            for(int i = 0; i<5; i++){
                if(parentDir == null){
                    JOptionPane.showMessageDialog(this,
                            "Error creating summary file: LootTables directory not found.",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                for(File fileInDirectory : Objects.requireNonNull(parentDir.listFiles())){
                    String name = fileInDirectory.getName();
                    if (name.equals("LootTables") || name.equals("Items")) {
                        parentDir = fileInDirectory.getParentFile();
                        found = true;
                        break outerLoop;
                    }
                }
                parentDir = parentDir.getParentFile();
            }


            if(!found){
                JOptionPane.showMessageDialog(this,
                        "Error creating summary file: LootTables directory not found. Probably it is too far from the first selected file.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            File parentDirToLootTables = parentDir;
            File summaryFile = new File(Arrays.stream(parentDir.listFiles()).filter((file)-> file
                            .getName().equals("LootTables")).findFirst()
                    .orElse(createLootTablesDir(parentDirToLootTables)), "LootTable_Shop_WeaponRack_T%d.json".formatted(tier));
            try (FileWriter writer = new FileWriter(summaryFile)) {
                writer.write(summaryJson.toString(2));
            }
            JOptionPane.showMessageDialog(this,
                    "Generated:\n" + summaryFile.getAbsolutePath(),
                    "Processing Complete",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch(IOException|JSONException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error creating summary file: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public File createLootTablesDir(File parentDirToLootTables){
        File ltDir = new File(parentDirToLootTables, "LootTables");
        if(!ltDir.mkdir()){
            JOptionPane.showMessageDialog(this,
                    "Error creating summary file: LootTables directory not found.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return ltDir;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BSItemsApp());
    }
}