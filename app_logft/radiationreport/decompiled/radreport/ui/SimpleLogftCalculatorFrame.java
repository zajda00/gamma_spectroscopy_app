/*
 * Decompiled with CFR 0.152.
 */
package radreport.ui;

import ame.base.AMEConfig;
import ame.ui.RestrictedFileSystemView;
import ensdfparser.calc.XDX;
import ensdfparser.ensdf.Beta;
import ensdfparser.ensdf.Decay;
import ensdfparser.ensdf.ECBP;
import ensdfparser.ensdf.ENSDF;
import ensdfparser.ensdf.Level;
import ensdfparser.ensdf.Nucleus;
import ensdfparser.ensdf.Parent;
import ensdfparser.ensdf.SDS2XDX;
import ensdfparser.ensdf.XDX2SDS;
import ensdfparser.nds.ensdf.EnsdfConfig;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.ensdf.MassChain;
import ensdfparser.nds.latex.Translator;
import ensdfparser.nds.util.Str;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Vector;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.GroupLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.LayoutStyle;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileSystemView;
import radreport.data.LogftCategory;
import radreport.data.LogftSystematics;
import radreport.decay.DecayDataset;
import radreport.decay.DecaySpectrum;
import radreport.decay.RADControl;
import radreport.main.Setup;
import radreport.ui.LogftReferenceFrame;
import radreport.ui.LogftResultFrame;

public class SimpleLogftCalculatorFrame
extends JFrame {
    private static final long serialVersionUID = 1L;
    private JPanel mainPanel;
    private JLabel lblNewLabel;
    private JPanel nuclidePanel;
    private JTextField nuclideTextField;
    private JLabel orLabel;
    private JTextField ATextField;
    private JLabel ZLabel;
    private JTextField ZTextField;
    private JPanel T12Panel;
    private JLabel T12Label;
    private JTextField T12TextField;
    private JTextField DTTextField;
    private JComboBox<Object> TUnitComboBox;
    private JPanel elPanel;
    private JLabel pLevelLabel;
    private JTextField EPtextField;
    private JTextField DEPtextField;
    private JPanel QValuePanel;
    private JLabel QVLabel;
    private JTextField QATextField;
    private JTextField DQATextField;
    private JLabel QUnitLabel;
    private JLabel titleLabel;
    private JSeparator R0Separator;
    private JTextField ELtextField;
    private JTextField DELtextField;
    private JToggleButton calculateLogftButton;
    private JPanel tiPanel;
    private JLabel resultLabel;
    private JSeparator resultSeparator;
    Vector<String> linesV = new Vector();
    private JPanel uncStylePanel;
    private JLabel uncStyyleLabel;
    private JSeparator uncStyleSeparator;
    private JRadioButton ENSDFStyleRadioButton;
    private LogftResultFrame logftResultFrame = null;
    LogftReferenceFrame logftReferenceFrame = null;
    private boolean isENSDFStyleUnc = true;
    private boolean isBetaMinus = true;
    private boolean isCalculateBR = false;
    private boolean useSelectedUniqueness = false;
    private String pNUCID = "";
    private String dNUCID = "";
    private String ts = "";
    private String dts = "";
    private String tunit = "S";
    private String eps = "0";
    private String deps = "";
    private String jps = "";
    private String jfs = "";
    private String qs = "";
    private String dqs = "";
    private String un = "";
    private String els = "";
    private String dels = "";
    private String tis = "";
    private String dtis = "";
    private String logfts = "";
    private String dlogfts = "";
    private int A = -1;
    private int Z = -1;
    private JRadioButton realStyleRadioButton;
    private ButtonGroup uncStyleButtonGroup;
    private ButtonGroup decayModeButtonGroup;
    private JLabel queLabel;
    ENSDF ens = null;
    DecaySpectrum spec = null;
    DecayDataset dataset = null;
    boolean isLoaded = false;
    private int defaultWidth = 430;
    private int defaultHeight = 795;
    private JLabel dmLabel;
    private JRadioButton betaDMButton;
    private JRadioButton ecbpDMButton;
    private JPanel logftPanel;
    private JTextField TItextField;
    private JTextField DTItextField;
    private JLabel tiLabel;
    private JLabel levelLabel;
    private JPanel dmPanel;
    private JLabel detailedResultLabel;
    private JTextField JFtextField;
    private JLabel jfLabel;
    private JTextField JPtextField;
    private JLabel jpLabel;
    private JToggleButton loadENSDFButton;
    private JLabel loadLabel;
    private JPanel tiPanel_1;
    private JLabel logftLabel;
    private JTextField logftTextField;
    private JTextField dlogftTextField;
    private JToggleButton calculateBranchingButton;
    private JComboBox<Object> unComboBox;
    private JCheckBox setUNCheckBox;

    public SimpleLogftCalculatorFrame() {
        try {
            Translator.init();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        Setup.load();
        long startTime = System.currentTimeMillis();
        this.setResizable(false);
        this.setDefaultCloseOperation(3);
        this.mainPanel = new JPanel();
        this.titleLabel = new JLabel("logft calculator");
        this.titleLabel.setHorizontalAlignment(0);
        this.titleLabel.setToolTipText("version: " + RADControl.version);
        this.titleLabel.setFont(new Font("SansSerif", 1, 14));
        this.uncStylePanel = new JPanel();
        this.uncStyyleLabel = new JLabel("Uncertainty Style:");
        this.uncStyyleLabel.setToolTipText("<HTML>set input  uncertainty style to be either ENSDF style,<br>like 1.23(3) or real-value style like 1.23(0.03)</HTML>");
        this.ENSDFStyleRadioButton = new JRadioButton("ENSDF");
        this.ENSDFStyleRadioButton.setToolTipText("ENSDF-style uncertainty, like 1.23(3)");
        this.ENSDFStyleRadioButton.setSelected(this.isENSDFStyleUnc);
        this.ENSDFStyleRadioButton.setEnabled(true);
        this.ENSDFStyleRadioButton.addItemListener(new ItemListener(){

            @Override
            public void itemStateChanged(ItemEvent e) {
                if (((JRadioButton)e.getSource()).isSelected()) {
                    SimpleLogftCalculatorFrame.this.isENSDFStyleUnc = true;
                }
            }
        });
        this.realStyleRadioButton = new JRadioButton("real-value");
        this.realStyleRadioButton.setToolTipText("real-value style uncertainty, like 1.23(0.03)");
        this.realStyleRadioButton.setSelected(!this.isENSDFStyleUnc);
        this.realStyleRadioButton.setEnabled(true);
        this.realStyleRadioButton.addItemListener(new ItemListener(){

            @Override
            public void itemStateChanged(ItemEvent e) {
                if (((JRadioButton)e.getSource()).isSelected()) {
                    SimpleLogftCalculatorFrame.this.isENSDFStyleUnc = false;
                }
            }
        });
        this.uncStyleButtonGroup = new ButtonGroup();
        this.uncStyleButtonGroup.add(this.ENSDFStyleRadioButton);
        this.uncStyleButtonGroup.add(this.realStyleRadioButton);
        GroupLayout gl_uncStylePanel = new GroupLayout(this.uncStylePanel);
        gl_uncStylePanel.setHorizontalGroup(gl_uncStylePanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_uncStylePanel.createSequentialGroup().addGap(30).addComponent(this.uncStyyleLabel, -2, 100, -2).addGap(10).addComponent(this.ENSDFStyleRadioButton, -2, 71, -2).addGap(5).addComponent(this.realStyleRadioButton, -1, 94, Short.MAX_VALUE).addContainerGap()));
        gl_uncStylePanel.setVerticalGroup(gl_uncStylePanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_uncStylePanel.createSequentialGroup().addGroup(gl_uncStylePanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_uncStylePanel.createSequentialGroup().addGap(2).addComponent(this.uncStyyleLabel, -2, 22, -2)).addGroup(gl_uncStylePanel.createSequentialGroup().addGap(4).addGroup(gl_uncStylePanel.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(this.realStyleRadioButton, -2, 18, -2).addComponent(this.ENSDFStyleRadioButton, -2, 18, -2)))).addContainerGap(-1, Short.MAX_VALUE)));
        this.uncStylePanel.setLayout(gl_uncStylePanel);
        this.uncStyleSeparator = new JSeparator();
        this.queLabel = new JLabel("?");
        this.queLabel.addMouseListener(new MouseAdapter(){

            @Override
            public void mouseClicked(MouseEvent e) {
                SimpleLogftCalculatorFrame.this.showReferenceFrame(e);
            }
        });
        this.queLabel.setFont(new Font("SansSerif", 1, 12));
        this.queLabel.setForeground(new Color(0, 0, 255));
        this.loadENSDFButton = new JToggleButton();
        this.loadENSDFButton.addActionListener(new ActionListener(){

            @Override
            public void actionPerformed(ActionEvent arg0) {
                SimpleLogftCalculatorFrame.this.loadButtonActionPerformed(arg0);
            }
        });
        this.loadENSDFButton.setToolTipText("load an ENSDF decay dataset to get parent information");
        this.loadENSDFButton.setText("load parent info from ENSDF");
        this.loadLabel = new JLabel("or type below");
        this.loadLabel.setToolTipText("");
        this.loadLabel.setHorizontalAlignment(2);
        GroupLayout groupLayout = new GroupLayout(this.getContentPane());
        groupLayout.setHorizontalGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.TRAILING).addGroup(groupLayout.createSequentialGroup().addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(groupLayout.createSequentialGroup().addContainerGap().addComponent(this.uncStylePanel, -2, 296, -2)).addGroup(groupLayout.createSequentialGroup().addGap(56).addComponent(this.titleLabel, -2, 234, -2).addGap(47).addComponent(this.queLabel, -2, 10, -2))).addContainerGap()).addGroup(groupLayout.createSequentialGroup().addGap(15).addComponent(this.loadENSDFButton, -2, 193, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.loadLabel, -2, 81, -2).addContainerGap(44, Short.MAX_VALUE)).addGroup(groupLayout.createSequentialGroup().addGap(1).addComponent(this.mainPanel, -2, 335, Short.MAX_VALUE).addGap(1)).addGroup(groupLayout.createSequentialGroup().addGap(14).addComponent(this.uncStyleSeparator, -1, 309, Short.MAX_VALUE).addGap(14)));
        groupLayout.setVerticalGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(groupLayout.createSequentialGroup().addContainerGap(-1, Short.MAX_VALUE).addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(this.titleLabel).addComponent(this.queLabel)).addGap(4).addComponent(this.uncStylePanel, -2, 26, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.uncStyleSeparator, -2, -1, -2).addGap(2).addGroup(groupLayout.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(this.loadENSDFButton).addComponent(this.loadLabel, -2, 22, -2)).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.mainPanel, -2, -1, -2).addGap(1)));
        this.nuclidePanel = new JPanel();
        this.lblNewLabel = new JLabel("parent:");
        this.lblNewLabel.setHorizontalAlignment(0);
        this.lblNewLabel.setToolTipText("name of alpha-decay parent, e.g., 164Os");
        this.nuclideTextField = new JTextField();
        this.nuclideTextField.setFont(new Font("SansSerif", 0, 11));
        this.nuclideTextField.setToolTipText("nuclide name of decay parent, e.g., 164Os");
        this.nuclideTextField.setColumns(10);
        this.nuclideTextField.addKeyListener(new KeyAdapter(){

            @Override
            public void keyReleased(KeyEvent e) {
                if (SimpleLogftCalculatorFrame.this.nuclideTextField != null) {
                    if (SimpleLogftCalculatorFrame.this.nuclideTextField.getText().trim().isEmpty()) {
                        SimpleLogftCalculatorFrame.this.ATextField.setEnabled(true);
                        SimpleLogftCalculatorFrame.this.ZTextField.setEnabled(true);
                    } else {
                        SimpleLogftCalculatorFrame.this.ATextField.setEnabled(false);
                        SimpleLogftCalculatorFrame.this.ZTextField.setEnabled(false);
                    }
                }
            }
        });
        this.nuclideTextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                String errMsg = "Error: invalid input for nuclide name!";
                try {
                    String s = SimpleLogftCalculatorFrame.this.nuclideTextField.getText().trim();
                    SimpleLogftCalculatorFrame.this.pNUCID = s;
                    if (!s.isEmpty()) {
                        SimpleLogftCalculatorFrame.this.ATextField.setEnabled(false);
                        SimpleLogftCalculatorFrame.this.ZTextField.setEnabled(false);
                        s = s.toUpperCase();
                        Nucleus nuc = new Nucleus(s);
                        int A1 = nuc.A();
                        int Z1 = nuc.Z();
                        if (A1 <= 0 || Z1 <= 0 || Z1 >= A1 || A1 > 4 * Z1) {
                            return;
                        }
                        SimpleLogftCalculatorFrame.this.pNUCID = nuc.nameENSDF();
                        SimpleLogftCalculatorFrame.this.A = nuc.A();
                        SimpleLogftCalculatorFrame.this.Z = nuc.Z();
                    } else {
                        SimpleLogftCalculatorFrame.this.A = -1;
                        SimpleLogftCalculatorFrame.this.Z = -1;
                        SimpleLogftCalculatorFrame.this.ATextField.setEnabled(true);
                        SimpleLogftCalculatorFrame.this.ZTextField.setEnabled(true);
                    }
                }
                catch (Exception e1) {
                    return;
                }
            }
        });
        this.orLabel = new JLabel("or A=");
        this.ATextField = new JTextField();
        this.ATextField.addKeyListener(new KeyAdapter(){

            @Override
            public void keyReleased(KeyEvent e) {
                try {
                    if (SimpleLogftCalculatorFrame.this.ATextField.getText().trim().isEmpty() && SimpleLogftCalculatorFrame.this.ZTextField.getText().trim().isEmpty()) {
                        SimpleLogftCalculatorFrame.this.nuclideTextField.setEnabled(true);
                    } else {
                        SimpleLogftCalculatorFrame.this.nuclideTextField.setEnabled(false);
                    }
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        });
        this.ATextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                String errMsg = "Error: invalid input for A!";
                String s = SimpleLogftCalculatorFrame.this.ATextField.getText().trim();
                SimpleLogftCalculatorFrame.this.A = -1;
                if (!s.isEmpty()) {
                    SimpleLogftCalculatorFrame.this.nuclideTextField.setEnabled(false);
                    try {
                        SimpleLogftCalculatorFrame.this.A = Integer.parseInt(s);
                    }
                    catch (NumberFormatException e1) {
                        return;
                    }
                } else if (SimpleLogftCalculatorFrame.this.ZTextField.getText().trim().isEmpty()) {
                    SimpleLogftCalculatorFrame.this.nuclideTextField.setEnabled(true);
                }
            }
        });
        this.ATextField.setColumns(10);
        this.ZLabel = new JLabel("Z=");
        this.ZLabel.setHorizontalAlignment(4);
        this.ZTextField = new JTextField();
        this.ZTextField.setColumns(10);
        this.ZTextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                String errMsg = "Error: invalid input for Z!";
                String s = SimpleLogftCalculatorFrame.this.ZTextField.getText().trim();
                SimpleLogftCalculatorFrame.this.Z = -1;
                if (!s.isEmpty()) {
                    SimpleLogftCalculatorFrame.this.nuclideTextField.setEnabled(false);
                    try {
                        SimpleLogftCalculatorFrame.this.Z = Integer.parseInt(s);
                    }
                    catch (NumberFormatException e1) {
                        return;
                    }
                } else if (SimpleLogftCalculatorFrame.this.ATextField.getText().trim().isEmpty()) {
                    SimpleLogftCalculatorFrame.this.nuclideTextField.setEnabled(true);
                }
            }
        });
        this.ZTextField.addKeyListener(new KeyAdapter(){

            @Override
            public void keyReleased(KeyEvent e) {
                try {
                    if (SimpleLogftCalculatorFrame.this.ATextField.getText().trim().isEmpty() && SimpleLogftCalculatorFrame.this.ZTextField.getText().trim().isEmpty()) {
                        SimpleLogftCalculatorFrame.this.nuclideTextField.setEnabled(true);
                    } else {
                        SimpleLogftCalculatorFrame.this.nuclideTextField.setEnabled(false);
                    }
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        });
        GroupLayout gl_nuclidePanel = new GroupLayout(this.nuclidePanel);
        gl_nuclidePanel.setHorizontalGroup(gl_nuclidePanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_nuclidePanel.createSequentialGroup().addContainerGap().addComponent(this.lblNewLabel, -2, 63, -2).addGap(1).addComponent(this.nuclideTextField, -2, 60, -2).addGap(25).addComponent(this.orLabel, -2, 39, -2).addGap(1).addComponent(this.ATextField, -2, 43, -2).addGap(2).addComponent(this.ZLabel, -2, 25, -2).addGap(1).addComponent(this.ZTextField, -2, 43, -2).addContainerGap(-1, Short.MAX_VALUE)));
        gl_nuclidePanel.setVerticalGroup(gl_nuclidePanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_nuclidePanel.createSequentialGroup().addGroup(gl_nuclidePanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_nuclidePanel.createSequentialGroup().addGap(2).addComponent(this.lblNewLabel, -2, 22, -2)).addGroup(gl_nuclidePanel.createSequentialGroup().addGap(1).addGroup(gl_nuclidePanel.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(this.nuclideTextField, -2, 24, -2).addComponent(this.orLabel).addComponent(this.ATextField, -2, 24, -2).addComponent(this.ZLabel).addComponent(this.ZTextField, -2, 24, -2)))).addContainerGap(-1, Short.MAX_VALUE)));
        this.nuclidePanel.setLayout(gl_nuclidePanel);
        this.T12Panel = new JPanel();
        this.T12Label = new JLabel("<HTML>T<sub>1/2</sub>=</HTML>");
        this.T12Label.setHorizontalAlignment(4);
        this.T12Label.setToolTipText("parent half-life");
        this.T12TextField = new JTextField();
        this.T12TextField.setToolTipText("half-life value");
        this.T12TextField.setColumns(10);
        this.T12TextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                SimpleLogftCalculatorFrame.this.ts = SimpleLogftCalculatorFrame.this.valueFieldFocusLost(e, "T");
            }
        });
        this.DTTextField = new JTextField();
        this.DTTextField.setToolTipText("half-life uncertainty; asymmetric uncertainty is accpeted");
        this.DTTextField.setColumns(10);
        this.DTTextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                SimpleLogftCalculatorFrame.this.dts = SimpleLogftCalculatorFrame.this.uncertaintyFieldFocusLost(e, "T");
            }
        });
        this.TUnitComboBox = new JComboBox<Object>(new Object[0]);
        this.TUnitComboBox.setModel(new DefaultComboBoxModel<String>(new String[]{"s", "ns", "<HTML>&mu;s", "ms", "m", "h", "d", "y"}));
        this.TUnitComboBox.addActionListener(new ActionListener(){

            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    String s = (String)((JComboBox)e.getSource()).getSelectedItem();
                    s = s.toUpperCase();
                    if (s.contains("MU")) {
                        SimpleLogftCalculatorFrame.this.tunit = "US";
                    } else {
                        SimpleLogftCalculatorFrame.this.tunit = s;
                    }
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        });
        GroupLayout gl_T12Panel = new GroupLayout(this.T12Panel);
        gl_T12Panel.setHorizontalGroup(gl_T12Panel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_T12Panel.createSequentialGroup().addGap(12).addComponent(this.T12Label, -2, 58, -2).addGap(1).addComponent(this.T12TextField, -2, 60, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.DTTextField, -2, 40, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.TUnitComboBox, -2, 67, -2).addContainerGap(44, Short.MAX_VALUE)));
        gl_T12Panel.setVerticalGroup(gl_T12Panel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_T12Panel.createSequentialGroup().addGroup(gl_T12Panel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_T12Panel.createSequentialGroup().addGap(1).addGroup(gl_T12Panel.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(this.T12TextField, -2, 24, -2).addComponent(this.DTTextField, -2, 24, -2).addComponent(this.TUnitComboBox, -2, -1, -2))).addGroup(gl_T12Panel.createSequentialGroup().addGap(2).addComponent(this.T12Label, -2, 22, -2))).addContainerGap(-1, Short.MAX_VALUE)));
        this.T12Panel.setLayout(gl_T12Panel);
        this.elPanel = new JPanel();
        this.pLevelLabel = new JLabel("E(level)=");
        this.pLevelLabel.setHorizontalAlignment(4);
        this.pLevelLabel.setToolTipText("parent-level energy");
        this.EPtextField = new JTextField();
        this.EPtextField.setToolTipText("alpha-decay branching in percentage");
        this.EPtextField.setColumns(10);
        this.EPtextField.setText("0");
        this.EPtextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                SimpleLogftCalculatorFrame.this.eps = SimpleLogftCalculatorFrame.this.valueFieldFocusLost(e, "E(parent)");
            }
        });
        this.DEPtextField = new JTextField();
        this.DEPtextField.setToolTipText("branching ratio uncertainty");
        this.DEPtextField.setColumns(10);
        this.DEPtextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                SimpleLogftCalculatorFrame.this.deps = SimpleLogftCalculatorFrame.this.uncertaintyFieldFocusLost(e, "E(parent)");
            }
        });
        this.jpLabel = new JLabel("<HTML>J<sup>&pi</sup>=</HTML>");
        this.jpLabel.setToolTipText("If not given, decay will be calculated as allowed.");
        this.jpLabel.setHorizontalAlignment(4);
        this.JPtextField = new JTextField();
        this.JPtextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                String errMsg = "Error: invalid input for parent JPI!";
                SimpleLogftCalculatorFrame.this.jps = ((JTextField)e.getSource()).getText().trim();
                if (!EnsdfUtil.isJPIStr(SimpleLogftCalculatorFrame.this.jps)) {
                    JOptionPane.showMessageDialog(null, errMsg);
                    SimpleLogftCalculatorFrame.this.jps = "";
                    return;
                }
            }
        });
        this.JPtextField.setToolTipText("If not given, decay will be calculated as allowed");
        this.JPtextField.setColumns(10);
        GroupLayout gl_elPanel = new GroupLayout(this.elPanel);
        gl_elPanel.setHorizontalGroup(gl_elPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_elPanel.createSequentialGroup().addGap(12).addComponent(this.pLevelLabel, -2, 58, -2).addGap(1).addComponent(this.EPtextField, -2, 60, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.DEPtextField, -2, 40, -2).addGap(37).addComponent(this.jpLabel, -2, 27, -2).addGap(1).addComponent(this.JPtextField, -2, 43, -2).addContainerGap(41, Short.MAX_VALUE)));
        gl_elPanel.setVerticalGroup(gl_elPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_elPanel.createSequentialGroup().addGap(1).addGroup(gl_elPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_elPanel.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(this.EPtextField, -2, 24, -2).addComponent(this.DEPtextField, -2, 24, -2).addComponent(this.pLevelLabel, -2, 22, -2)).addComponent(this.JPtextField, -2, 24, -2).addComponent(this.jpLabel, -2, 16, -2)).addContainerGap(-1, Short.MAX_VALUE)));
        this.elPanel.setLayout(gl_elPanel);
        this.QValuePanel = new JPanel();
        this.QVLabel = new JLabel("Q-value=");
        this.QVLabel.setHorizontalAlignment(4);
        this.QVLabel.setToolTipText("alpha-decay Q-value");
        this.QATextField = new JTextField();
        this.QATextField.setToolTipText("alpha-decay Q-Value in keV. If left blank, QA will be taken from AME2020 if available.");
        this.QATextField.setColumns(10);
        this.QATextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                SimpleLogftCalculatorFrame.this.qs = SimpleLogftCalculatorFrame.this.valueFieldFocusLost(e, "QA");
            }
        });
        this.DQATextField = new JTextField();
        this.DQATextField.setToolTipText("Q-Value uncertainty");
        this.DQATextField.setColumns(10);
        this.DQATextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                SimpleLogftCalculatorFrame.this.dqs = SimpleLogftCalculatorFrame.this.uncertaintyFieldFocusLost(e, "Q");
            }
        });
        this.QUnitLabel = new JLabel("keV");
        GroupLayout gl_QValuePanel = new GroupLayout(this.QValuePanel);
        gl_QValuePanel.setHorizontalGroup(gl_QValuePanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_QValuePanel.createSequentialGroup().addGap(12).addComponent(this.QVLabel, -2, 58, -2).addGap(1).addComponent(this.QATextField, -2, 60, -2).addGap(6).addComponent(this.DQATextField, -2, 40, -2).addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED).addComponent(this.QUnitLabel, -2, 34, -2).addContainerGap(41, Short.MAX_VALUE)));
        gl_QValuePanel.setVerticalGroup(gl_QValuePanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_QValuePanel.createSequentialGroup().addGroup(gl_QValuePanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_QValuePanel.createSequentialGroup().addGap(1).addGroup(gl_QValuePanel.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(this.QATextField, -2, 24, -2).addGroup(gl_QValuePanel.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(this.DQATextField, -2, 24, -2).addComponent(this.QUnitLabel)))).addGroup(gl_QValuePanel.createSequentialGroup().addGap(2).addComponent(this.QVLabel, -2, 22, -2))).addContainerGap(-1, Short.MAX_VALUE)));
        this.QValuePanel.setLayout(gl_QValuePanel);
        this.R0Separator = new JSeparator();
        this.logftPanel = new JPanel();
        this.logftPanel.setBorder(new TitledBorder(null, "calculate logft of a final level", 4, 2, null, new Color(59, 59, 59)));
        this.calculateLogftButton = new JToggleButton();
        this.calculateLogftButton.addActionListener(new ActionListener(){

            @Override
            public void actionPerformed(ActionEvent e) {
                SimpleLogftCalculatorFrame.this.calculateLogftButtonActionPerformed();
            }
        });
        this.calculateLogftButton.setToolTipText("calculate logft of decay to this level");
        this.calculateLogftButton.setText("calculate logft");
        JPanel levelPanel = new JPanel();
        this.levelLabel = new JLabel("E(level)=");
        this.levelLabel.setHorizontalAlignment(4);
        this.levelLabel.setToolTipText("daughter level energy in keV");
        this.ELtextField = new JTextField();
        this.ELtextField.setToolTipText("level enegy in keV");
        this.ELtextField.setColumns(10);
        this.ELtextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                SimpleLogftCalculatorFrame.this.els = SimpleLogftCalculatorFrame.this.valueFieldFocusLost(e, "E(level)");
            }
        });
        this.DELtextField = new JTextField();
        this.DELtextField.setToolTipText("energy uncertainty");
        this.DELtextField.setColumns(10);
        this.DELtextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                SimpleLogftCalculatorFrame.this.dels = SimpleLogftCalculatorFrame.this.uncertaintyFieldFocusLost(e, "E(level)");
            }
        });
        this.jfLabel = new JLabel("<HTML>J<sup>&pi</sup>=</HTML>");
        this.jfLabel.setToolTipText("If not given, decay will be calculated as allowed or based on the selection of uniqueness if set.");
        this.jfLabel.setHorizontalAlignment(4);
        this.JFtextField = new JTextField();
        this.JFtextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                String errMsg = "Error: invalid input for daughter level JPI!";
                SimpleLogftCalculatorFrame.this.jfs = ((JTextField)e.getSource()).getText().trim();
                if (!EnsdfUtil.isJPIStr(SimpleLogftCalculatorFrame.this.jfs)) {
                    JOptionPane.showMessageDialog(null, errMsg);
                    SimpleLogftCalculatorFrame.this.jfs = "";
                    return;
                }
            }
        });
        this.JFtextField.setToolTipText("If not given, decay will be calculated as allowed or based on the selection of uniqueness if set");
        this.JFtextField.setColumns(10);
        GroupLayout gl_levelPanel = new GroupLayout(levelPanel);
        gl_levelPanel.setHorizontalGroup(gl_levelPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_levelPanel.createSequentialGroup().addGap(1).addComponent(this.levelLabel, -2, 58, -2).addGap(1).addComponent(this.ELtextField, -2, 60, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.DELtextField, -2, 40, -2).addGap(37).addComponent(this.jfLabel, -2, 25, -2).addGap(1).addComponent(this.JFtextField, -2, 43, -2).addContainerGap(47, Short.MAX_VALUE)));
        gl_levelPanel.setVerticalGroup(gl_levelPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_levelPanel.createSequentialGroup().addGroup(gl_levelPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_levelPanel.createSequentialGroup().addGap(1).addGroup(gl_levelPanel.createParallelGroup(GroupLayout.Alignment.BASELINE).addComponent(this.ELtextField, -2, 24, -2).addComponent(this.DELtextField, -2, 24, -2))).addGroup(gl_levelPanel.createSequentialGroup().addGap(2).addComponent(this.levelLabel, -2, 22, -2)).addGroup(gl_levelPanel.createSequentialGroup().addGap(1).addComponent(this.jfLabel, -2, 16, -2)).addGroup(gl_levelPanel.createSequentialGroup().addGap(1).addComponent(this.JFtextField, -2, 24, -2))).addContainerGap(-1, Short.MAX_VALUE)));
        levelPanel.setLayout(gl_levelPanel);
        this.tiPanel = new JPanel();
        this.tiLabel = new JLabel("<HTML>%I(total)=</HTML>");
        this.tiLabel.setHorizontalAlignment(4);
        this.tiLabel.setToolTipText("<HTML>absolute total intensity of decay to this state per 100 parent decays");
        this.TItextField = new JTextField();
        this.TItextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                SimpleLogftCalculatorFrame.this.tis = SimpleLogftCalculatorFrame.this.valueFieldFocusLost(e, "%I(total)");
                if (Str.isNumeric(SimpleLogftCalculatorFrame.this.tis) && Double.parseDouble(SimpleLogftCalculatorFrame.this.tis) > 100.0) {
                    JOptionPane.showMessageDialog(null, "Error: wrong input %I(total) value>100");
                    return;
                }
            }
        });
        this.TItextField.setToolTipText("absolute total intensity to this level in percentage");
        this.TItextField.setColumns(10);
        this.DTItextField = new JTextField();
        this.DTItextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                SimpleLogftCalculatorFrame.this.dtis = SimpleLogftCalculatorFrame.this.uncertaintyFieldFocusLost(e, "%I(total)");
                if (Str.isNumeric(SimpleLogftCalculatorFrame.this.dtis) && Double.parseDouble(SimpleLogftCalculatorFrame.this.dtis) > 100.0) {
                    JOptionPane.showMessageDialog(null, "Error: wrong input %I(total) uncertainty>100");
                    return;
                }
            }
        });
        this.DTItextField.setToolTipText("intensity uncertainty");
        this.DTItextField.setColumns(10);
        this.unComboBox = new JComboBox<Object>(new Object[0]);
        this.unComboBox.setEnabled(this.useSelectedUniqueness);
        this.unComboBox.setModel(new DefaultComboBoxModel<String>(new String[]{"NO", "1U", "2U", "3U", "4U"}));
        this.unComboBox.addActionListener(new ActionListener(){

            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    String s = (String)((JComboBox)e.getSource()).getSelectedItem();
                    SimpleLogftCalculatorFrame.this.un = s.toUpperCase();
                    if (SimpleLogftCalculatorFrame.this.un.equals("NO")) {
                        SimpleLogftCalculatorFrame.this.un = "";
                    }
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        });
        this.setUNCheckBox = new JCheckBox("set unique");
        this.setUNCheckBox.addItemListener(new ItemListener(){

            @Override
            public void itemStateChanged(ItemEvent e) {
                if (((JCheckBox)e.getSource()).isSelected()) {
                    SimpleLogftCalculatorFrame.this.useSelectedUniqueness = true;
                    SimpleLogftCalculatorFrame.this.JFtextField.setEnabled(false);
                    SimpleLogftCalculatorFrame.this.unComboBox.setEnabled(true);
                } else {
                    SimpleLogftCalculatorFrame.this.useSelectedUniqueness = false;
                    SimpleLogftCalculatorFrame.this.JFtextField.setEnabled(true);
                    SimpleLogftCalculatorFrame.this.unComboBox.setEnabled(false);
                }
            }
        });
        this.setUNCheckBox.setToolTipText("If not checked, uniqueness will be determined from input JPIs of parent and daughter levels");
        this.setUNCheckBox.setHorizontalAlignment(2);
        GroupLayout gl_tiPanel = new GroupLayout(this.tiPanel);
        gl_tiPanel.setHorizontalGroup(gl_tiPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_tiPanel.createSequentialGroup().addGap(1).addComponent(this.tiLabel, -2, 58, -2).addGap(1).addComponent(this.TItextField, -2, 60, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.DTItextField, -2, 40, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 15, Short.MAX_VALUE).addComponent(this.setUNCheckBox, -2, 85, -2).addGap(1).addComponent(this.unComboBox, -2, 52, -2)));
        gl_tiPanel.setVerticalGroup(gl_tiPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_tiPanel.createSequentialGroup().addGroup(gl_tiPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_tiPanel.createSequentialGroup().addGap(1).addGroup(gl_tiPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(this.DTItextField, -2, 24, -2).addComponent(this.tiLabel, -2, 22, -2).addComponent(this.TItextField, -2, 24, -2))).addComponent(this.unComboBox, -2, -1, -2).addGroup(gl_tiPanel.createSequentialGroup().addGap(4).addComponent(this.setUNCheckBox))).addContainerGap(-1, Short.MAX_VALUE)));
        this.tiPanel.setLayout(gl_tiPanel);
        this.resultLabel = new JLabel("");
        this.resultLabel.setVerticalAlignment(1);
        this.resultLabel.setForeground(new Color(30, 144, 255));
        this.resultSeparator = new JSeparator();
        this.detailedResultLabel = new JLabel("<HTML><center>detailed<br>results</center></HTML>");
        this.detailedResultLabel.addMouseListener(new MouseAdapter(){

            @Override
            public void mouseClicked(MouseEvent e) {
                SimpleLogftCalculatorFrame.this.showLogftResultFrame();
            }
        });
        this.detailedResultLabel.setHorizontalAlignment(0);
        this.detailedResultLabel.setForeground(Color.BLUE);
        this.detailedResultLabel.setFont(new Font("SansSerif", 0, 10));
        this.tiPanel_1 = new JPanel();
        this.logftLabel = new JLabel("<HTML>logft=</HTML>");
        this.logftLabel.setToolTipText("<HTML>input logft value for calculating corresponding branching</HTML>");
        this.logftLabel.setHorizontalAlignment(4);
        this.logftTextField = new JTextField();
        this.logftTextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                SimpleLogftCalculatorFrame.this.logfts = SimpleLogftCalculatorFrame.this.valueFieldFocusLost(e, "logft");
                if (!Str.isNumeric(SimpleLogftCalculatorFrame.this.logfts) || Double.parseDouble(SimpleLogftCalculatorFrame.this.logfts) <= 0.0) {
                    JOptionPane.showMessageDialog(null, "Error: wrong input logft value");
                    return;
                }
            }
        });
        this.logftTextField.setToolTipText("input logft value for calculating corresponding branching");
        this.logftTextField.setColumns(10);
        this.dlogftTextField = new JTextField();
        this.dlogftTextField.addFocusListener(new FocusAdapter(){

            @Override
            public void focusLost(FocusEvent e) {
                SimpleLogftCalculatorFrame.this.dlogfts = SimpleLogftCalculatorFrame.this.uncertaintyFieldFocusLost(e, "logft");
            }
        });
        this.dlogftTextField.setToolTipText("intensity uncertainty");
        this.dlogftTextField.setColumns(10);
        GroupLayout gl_tiPanel_1 = new GroupLayout(this.tiPanel_1);
        gl_tiPanel_1.setHorizontalGroup(gl_tiPanel_1.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_tiPanel_1.createSequentialGroup().addGap(1).addComponent(this.logftLabel, -2, 58, -2).addGap(1).addComponent(this.logftTextField, -2, 60, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.dlogftTextField, -2, 50, -2).addContainerGap(76, Short.MAX_VALUE)));
        gl_tiPanel_1.setVerticalGroup(gl_tiPanel_1.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_tiPanel_1.createSequentialGroup().addGap(1).addGroup(gl_tiPanel_1.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(this.logftLabel, -2, 22, -2).addComponent(this.logftTextField, -2, 24, -2).addComponent(this.dlogftTextField, -2, 24, -2)).addContainerGap()));
        this.tiPanel_1.setLayout(gl_tiPanel_1);
        this.calculateBranchingButton = new JToggleButton();
        this.calculateBranchingButton.addActionListener(new ActionListener(){

            @Override
            public void actionPerformed(ActionEvent arg0) {
                SimpleLogftCalculatorFrame.this.calculateBranchingButtonActionPerformed();
            }
        });
        this.calculateBranchingButton.setToolTipText("calculate branching for input logft of decay to this level");
        this.calculateBranchingButton.setText("calculate branching");
        GroupLayout gl_logftPanel = new GroupLayout(this.logftPanel);
        gl_logftPanel.setHorizontalGroup(gl_logftPanel.createParallelGroup(GroupLayout.Alignment.TRAILING).addGroup(gl_logftPanel.createSequentialGroup().addContainerGap(63, Short.MAX_VALUE).addGroup(gl_logftPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(this.calculateLogftButton, -2, 190, -2).addComponent(this.calculateBranchingButton, -2, 190, -2)).addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED).addComponent(this.detailedResultLabel, -2, 48, -2).addContainerGap()).addGroup(gl_logftPanel.createSequentialGroup().addGap(1).addComponent(this.tiPanel_1, -1, 254, Short.MAX_VALUE).addGap(66)).addComponent(this.tiPanel, GroupLayout.Alignment.LEADING, -1, 321, Short.MAX_VALUE).addGroup(gl_logftPanel.createSequentialGroup().addContainerGap().addComponent(this.resultLabel, -1, 301, Short.MAX_VALUE).addContainerGap()).addGroup(gl_logftPanel.createSequentialGroup().addContainerGap().addComponent(this.resultSeparator, -1, 301, Short.MAX_VALUE).addContainerGap()).addComponent(levelPanel, GroupLayout.Alignment.LEADING, -2, 321, -2));
        gl_logftPanel.setVerticalGroup(gl_logftPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_logftPanel.createSequentialGroup().addContainerGap().addComponent(levelPanel, -2, 26, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.tiPanel, -2, 26, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.calculateLogftButton, -2, 28, -2).addGap(12).addGroup(gl_logftPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addComponent(this.tiPanel_1, -2, 26, -2).addComponent(this.detailedResultLabel, -2, 31, -2)).addGap(1).addComponent(this.calculateBranchingButton).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.resultSeparator, -2, -1, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.resultLabel, -1, 106, Short.MAX_VALUE).addGap(1)));
        this.logftPanel.setLayout(gl_logftPanel);
        this.dmPanel = new JPanel();
        this.dmLabel = new JLabel("<HTML>decay mode: </HTML>");
        this.dmLabel.setToolTipText("decay mode");
        this.dmLabel.setHorizontalAlignment(4);
        this.betaDMButton = new JRadioButton("<HTML>&beta<sup>-</sup></HTML>");
        this.betaDMButton.addItemListener(new ItemListener(){

            @Override
            public void itemStateChanged(ItemEvent e) {
                if (((JRadioButton)e.getSource()).isSelected()) {
                    SimpleLogftCalculatorFrame.this.isBetaMinus = true;
                }
            }
        });
        this.betaDMButton.setSelected(this.isBetaMinus);
        this.betaDMButton.setEnabled(true);
        this.ecbpDMButton = new JRadioButton("<HTML>&epsilon/&beta<sup>+</sup></HTML>");
        this.ecbpDMButton.addItemListener(new ItemListener(){

            @Override
            public void itemStateChanged(ItemEvent e) {
                if (((JRadioButton)e.getSource()).isSelected()) {
                    SimpleLogftCalculatorFrame.this.isBetaMinus = false;
                }
            }
        });
        this.ecbpDMButton.setEnabled(true);
        this.decayModeButtonGroup = new ButtonGroup();
        this.decayModeButtonGroup.add(this.betaDMButton);
        this.decayModeButtonGroup.add(this.ecbpDMButton);
        GroupLayout gl_dmPanel = new GroupLayout(this.dmPanel);
        gl_dmPanel.setHorizontalGroup(gl_dmPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_dmPanel.createSequentialGroup().addContainerGap().addComponent(this.dmLabel, -2, 75, -2).addGap(18).addComponent(this.betaDMButton, -2, 51, -2).addGap(2).addComponent(this.ecbpDMButton, -2, 57, -2).addContainerGap(50, Short.MAX_VALUE)));
        gl_dmPanel.setVerticalGroup(gl_dmPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_dmPanel.createSequentialGroup().addGap(2).addGroup(gl_dmPanel.createParallelGroup(GroupLayout.Alignment.TRAILING).addComponent(this.dmLabel, -2, 22, -2).addGroup(gl_dmPanel.createSequentialGroup().addComponent(this.betaDMButton, -2, 18, -2).addGap(2)).addGroup(gl_dmPanel.createSequentialGroup().addComponent(this.ecbpDMButton, -2, 18, -2).addGap(2))).addContainerGap(12, Short.MAX_VALUE)));
        this.dmPanel.setLayout(gl_dmPanel);
        GroupLayout gl_mainPanel = new GroupLayout(this.mainPanel);
        gl_mainPanel.setHorizontalGroup(gl_mainPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_mainPanel.createSequentialGroup().addGap(2).addGroup(gl_mainPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_mainPanel.createSequentialGroup().addGap(7).addComponent(this.R0Separator, -1, 298, Short.MAX_VALUE)).addGroup(gl_mainPanel.createParallelGroup(GroupLayout.Alignment.TRAILING).addComponent(this.dmPanel, -2, 263, -2).addComponent(this.QValuePanel, -2, 263, -2)).addComponent(this.T12Panel, -2, 291, -2).addGroup(gl_mainPanel.createParallelGroup(GroupLayout.Alignment.TRAILING, false).addComponent(this.elPanel, GroupLayout.Alignment.LEADING, 0, 0, Short.MAX_VALUE).addComponent(this.nuclidePanel, GroupLayout.Alignment.LEADING, -1, -1, Short.MAX_VALUE))).addGap(7)).addGroup(gl_mainPanel.createSequentialGroup().addGap(1).addComponent(this.logftPanel, -1, 334, Short.MAX_VALUE).addGap(1)));
        gl_mainPanel.setVerticalGroup(gl_mainPanel.createParallelGroup(GroupLayout.Alignment.LEADING).addGroup(gl_mainPanel.createSequentialGroup().addGap(5).addComponent(this.nuclidePanel, -2, 26, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.elPanel, -2, 26, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addGroup(gl_mainPanel.createParallelGroup(GroupLayout.Alignment.TRAILING).addComponent(this.dmPanel, -2, 26, -2).addGroup(gl_mainPanel.createSequentialGroup().addComponent(this.T12Panel, -2, 26, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.QValuePanel, -2, 26, -2).addGap(34))).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.R0Separator, -2, 2, -2).addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(this.logftPanel, -1, 313, Short.MAX_VALUE).addGap(1)));
        this.mainPanel.setLayout(gl_mainPanel);
        this.getContentPane().setLayout(groupLayout);
        this.pack();
    }

    protected void loadButtonActionPerformed(ActionEvent arg0) {
        String msg = "";
        try {
            int ret;
            JFileChooser fc;
            try {
                fc = new JFileChooser(".");
            }
            catch (Exception e) {
                fc = new JFileChooser(".", (FileSystemView)new RestrictedFileSystemView());
            }
            if (Setup.filedir != null) {
                fc.setCurrentDirectory(new File(Setup.filedir));
            }
            if ((ret = fc.showOpenDialog(this)) == 0) {
                Setup.filedir = fc.getCurrentDirectory().toString();
                Setup.save();
                File dataFile = fc.getSelectedFile();
                MassChain data = new MassChain();
                data.load(dataFile);
                this.ens = data.getENSDF(0);
                String dsid = this.ens.DSId0();
                String decayType = this.ens.decayRecordType();
                if (!dsid.contains("DECAY") || !"BE".contains(decayType)) {
                    msg = "*Error*: wrong input file of decay dataset!\n";
                    JOptionPane.showMessageDialog(this, msg);
                    return;
                }
                if (this.ens.nParents() == 0) {
                    msg = "*Error*: Input decay dataset has no parent record!\n";
                    JOptionPane.showMessageDialog(this, msg);
                    return;
                }
                Parent p = this.ens.parentAt(0);
                Nucleus pnuc = p.nucleus();
                this.pNUCID = pnuc.nameENSDF();
                this.A = pnuc.A();
                this.Z = pnuc.z();
                Level pLev = p.level();
                this.eps = pLev.ES();
                this.deps = pLev.DES();
                this.jps = pLev.JPiS();
                this.ts = pLev.T12S();
                this.dts = pLev.DT12S();
                this.tunit = pLev.T12Unit();
                this.qs = p.QS();
                this.dqs = p.DQS();
                if (decayType.equals("B")) {
                    this.betaDMButton.setSelected(true);
                } else {
                    this.ecbpDMButton.setSelected(true);
                }
                this.ENSDFStyleRadioButton.setSelected(true);
                this.nuclideTextField.setText(this.pNUCID);
                this.ATextField.setText(String.valueOf(this.A));
                this.ZTextField.setText(String.valueOf(this.Z));
                this.EPtextField.setText(this.eps);
                this.DEPtextField.setText(this.deps);
                this.JPtextField.setText(this.jps);
                this.T12TextField.setText(this.ts);
                this.DTTextField.setText(this.dts);
                this.QATextField.setText(this.qs);
                this.DQATextField.setText(this.dqs);
                this.tunit = this.tunit.toLowerCase();
                int i = 0;
                while (i < this.TUnitComboBox.getItemCount()) {
                    String item = (String)this.TUnitComboBox.getItemAt(i);
                    if (this.tunit.equals(item) || this.tunit.equals("mu") && item.contains(this.tunit)) {
                        this.TUnitComboBox.setSelectedIndex(i);
                        break;
                    }
                    this.TUnitComboBox.setSelectedIndex(-1);
                    ++i;
                }
                if (this.ens.nLevels() > 0) {
                    Level lev = this.ens.levelAt(0);
                    for (Level l : this.ens.levelsV()) {
                        if (l.DecaysV().size() <= 0) continue;
                        lev = l;
                        break;
                    }
                    this.els = lev.ES();
                    this.dels = lev.DES();
                    this.jfs = lev.JPiS();
                    if (this.isBetaMinus && lev.nBetas() > 0) {
                        Beta beta = lev.betaAt(0);
                        this.tis = beta.RIS();
                        this.dtis = beta.DRIS();
                        this.logfts = beta.LOGFTS();
                        this.dlogfts = beta.DLOGFTS();
                        this.un = beta.unique();
                    } else if (!this.isBetaMinus && lev.nECBPs() > 0) {
                        ECBP ec = lev.ECBPAt(0);
                        this.tis = ec.ITS();
                        this.dtis = ec.DITS();
                        this.logfts = ec.LOGFTS();
                        this.dlogfts = ec.DLOGFTS();
                        if (this.tis.isEmpty()) {
                            if (ec.IBS().length() > 0 && ec.IES().isEmpty()) {
                                this.tis = ec.IBS();
                                this.dtis = ec.DIBS();
                            } else if (ec.IBS().isEmpty() && ec.IES().length() > 0) {
                                this.tis = ec.IES();
                                this.dtis = ec.DIES();
                            }
                        }
                        this.un = ec.unique();
                    }
                }
                this.un = this.un.trim().toUpperCase();
                boolean hasSetUN = false;
                if (this.un.length() == 2) {
                    this.unComboBox.setEnabled(true);
                    int i2 = 0;
                    while (i2 < this.unComboBox.getItemCount()) {
                        String item = (String)this.unComboBox.getItemAt(i2);
                        if (this.un.equals(item)) {
                            this.unComboBox.setSelectedIndex(i2);
                            hasSetUN = true;
                            this.useSelectedUniqueness = true;
                            this.setUNCheckBox.setSelected(true);
                            break;
                        }
                        ++i2;
                    }
                }
                if (!hasSetUN) {
                    this.unComboBox.setSelectedIndex(0);
                    this.unComboBox.setEnabled(false);
                    this.useSelectedUniqueness = false;
                    this.setUNCheckBox.setSelected(false);
                }
                this.ELtextField.setText(this.els);
                this.DELtextField.setText(this.dels);
                this.JFtextField.setText(this.jfs);
                this.TItextField.setText(this.tis);
                this.DTItextField.setText(this.dtis);
                this.logftTextField.setText(this.logfts);
                this.dlogftTextField.setText(this.dlogfts);
                String s = "";
                for (String line : this.ens.lines()) {
                    s = String.valueOf(s) + line + "\n";
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            msg = "*Error*: loading failed. Please check the file!\n";
            JOptionPane.showMessageDialog(this, msg);
        }
    }

    protected void updateLogftResultFrame() {
        if (this.logftResultFrame != null) {
            String result = this.printDetailedResults(this.spec, this.ens);
            this.logftResultFrame.updateResult(result);
        }
    }

    protected void showLogftResultFrame() {
        try {
            int width = 600;
            int height = this.defaultHeight;
            if (!this.isLoaded) {
                JOptionPane.showMessageDialog(this, "Error: no calculation has been done yet.");
                return;
            }
            String result = this.printDetailedResults(this.spec, this.ens);
            String title = "Detailed results of logft calculations";
            Frame[] frames = Frame.getFrames();
            int i = 0;
            while (i < frames.length) {
                if (frames[i].getTitle().equals(title)) {
                    this.logftResultFrame = (LogftResultFrame)frames[i];
                }
                ++i;
            }
            if (this.logftResultFrame == null) {
                this.logftResultFrame = new LogftResultFrame(result);
                this.logftResultFrame.setTitle(title);
                this.logftResultFrame.setLocation(this.getLocationOnScreen().x + this.getWidth() + 2, this.getLocationOnScreen().y);
                this.logftResultFrame.setDefaultCloseOperation(2);
                this.logftResultFrame.setVisible(true);
                this.logftResultFrame.pack();
                this.logftResultFrame.setResizable(false);
            } else {
                this.logftResultFrame.updateResult(result);
                if (!this.logftResultFrame.isVisible()) {
                    this.logftResultFrame.setLocation(this.getLocationOnScreen().x + this.getWidth() + 2, this.getLocationOnScreen().y);
                    this.logftResultFrame.setVisible(true);
                }
            }
            this.logftResultFrame.requestFocus();
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Error when openning window of detailed logft results.");
            return;
        }
    }

    protected void showReferenceFrame(MouseEvent e) {
        try {
            int width = 600;
            int height = this.defaultHeight;
            String title = "Logft calculation references";
            Frame[] frames = Frame.getFrames();
            int i = 0;
            while (i < frames.length) {
                if (frames[i].getTitle().equals(title)) {
                    this.logftReferenceFrame = (LogftReferenceFrame)frames[i];
                }
                ++i;
            }
            if (this.logftReferenceFrame == null) {
                this.logftReferenceFrame = new LogftReferenceFrame();
                this.logftReferenceFrame.setTitle(title);
                this.logftReferenceFrame.setLocation(this.getLocationOnScreen().x + this.getWidth() + 2, this.getLocationOnScreen().y);
                this.logftReferenceFrame.setDefaultCloseOperation(2);
                this.logftReferenceFrame.setVisible(true);
                this.logftReferenceFrame.pack();
                this.logftReferenceFrame.setResizable(false);
            } else if (!this.logftReferenceFrame.isVisible()) {
                this.logftReferenceFrame.setVisible(true);
                this.logftReferenceFrame.setLocation(this.getLocationOnScreen().x + this.getWidth() + 2, this.getLocationOnScreen().y);
            }
            this.logftReferenceFrame.requestFocus();
        }
        catch (Exception e1) {
            JOptionPane.showMessageDialog(null, "Error when openning window of logft references.");
            return;
        }
    }

    protected String valueFieldFocusLost(FocusEvent e, String name) {
        String errMsg = "Error: invalid input for " + name + " value!";
        String s = ((JTextField)e.getSource()).getText().trim();
        if (!s.isEmpty()) {
            try {
                double d = Double.parseDouble(s);
                if (d < 0.0) {
                    JOptionPane.showMessageDialog(this, errMsg);
                }
            }
            catch (NumberFormatException e1) {
                JOptionPane.showMessageDialog(this, errMsg);
            }
        }
        return s;
    }

    protected String uncertaintyFieldFocusLost(FocusEvent e, String name) {
        String errMsg = "Error: invalid input for " + name + " uncertainty!";
        String ds = ((JTextField)e.getSource()).getText().trim();
        String ds1 = "";
        String ds2 = "";
        if (!ds.isEmpty()) {
            try {
                ds = ds.toUpperCase();
                if (!Str.isNumeric(ds)) {
                    if (ds.length() == 2 && "LT,LE,GT,GE,AP,SY".contains(ds) && !ds.contains(",")) {
                        return ds;
                    }
                    if (ds.contains("+") && ds.contains("-") && (ds.startsWith("+") || ds.startsWith("-"))) {
                        ds1 = ds.substring(1).trim();
                        String[] temp = ds1.split("[+-]");
                        if (temp.length != 2 || !Str.isNumeric(temp[0]) || !Str.isNumeric(temp[1])) {
                            JOptionPane.showMessageDialog(this, errMsg);
                        } else {
                            ds1 = temp[0];
                            ds2 = temp[1];
                        }
                    } else {
                        JOptionPane.showMessageDialog(this, errMsg);
                    }
                }
                double dx = -1.0;
                double dxu = -1.0;
                double dxl = -1.0;
                if (this.isENSDFStyleUnc) {
                    if (Str.isNumeric(ds)) {
                        dx = Integer.parseInt(ds);
                    }
                    if (ds1.length() > 0) {
                        dxu = Integer.parseInt(ds1);
                    }
                    if (ds2.length() > 0) {
                        dxl = Integer.parseInt(ds2);
                    }
                } else {
                    if (Str.isNumeric(ds)) {
                        dx = Double.parseDouble(ds);
                    }
                    if (ds1.length() > 0) {
                        dxu = Double.parseDouble(ds1);
                    }
                    if (ds2.length() > 0) {
                        dxl = Double.parseDouble(ds2);
                    }
                }
                if (dx < 0.0 && (dxu < 0.0 || dxl < 0.0)) {
                    JOptionPane.showMessageDialog(this, errMsg);
                }
            }
            catch (NumberFormatException e1) {
                JOptionPane.showMessageDialog(this, errMsg);
            }
        }
        return ds;
    }

    SDS2XDX convertToENSDF(String s, String ds) {
        return SDS2XDX.parse(s, ds, this.isENSDFStyleUnc);
    }

    protected void calculateLogftButtonActionPerformed() {
        this.isCalculateBR = false;
        this.calculate();
    }

    protected void calculateBranchingButtonActionPerformed() {
        this.isCalculateBR = true;
        this.calculate();
    }

    protected void calculate() {
        block120: {
            this.resultLabel.setText("");
            boolean isUseQVFromAME = false;
            Vector<String> linesV = new Vector<String>();
            String prefix = "";
            Nucleus pnuc = new Nucleus();
            if (this.pNUCID.isEmpty()) {
                if (this.A <= 0 || this.Z <= 0) {
                    JOptionPane.showMessageDialog(this, "Error: parent nuclide is not specified or invalid");
                    return;
                }
                pnuc.setZA(this.Z, this.A);
                this.pNUCID = pnuc.nameENSDF();
                if (this.pNUCID == null || this.pNUCID.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Error: wrong input A or Z!\nParent nuclide does not exist!");
                    return;
                }
                if (this.A < 1 || this.Z < 1) {
                    JOptionPane.showMessageDialog(this, "Error: wrong input A or Z!");
                    return;
                }
            } else if (this.A <= 0) {
                try {
                    pnuc = new Nucleus(this.pNUCID);
                    this.A = pnuc.A();
                    this.Z = pnuc.Z();
                }
                catch (IOException e1) {
                    e1.printStackTrace();
                }
            } else {
                pnuc.setZA(this.Z, this.A);
            }
            if (AMEConfig.getMass(this.pNUCID) == null) {
                JOptionPane.showMessageDialog(this, "Error: wrong nuclide input!\nParent nuclide" + this.pNUCID + " does not exist!");
                return;
            }
            Nucleus dnuc = new Nucleus();
            if (this.isBetaMinus) {
                dnuc.setZA(this.Z + 1, this.A);
            } else {
                dnuc.setZA(this.Z - 1, this.A);
            }
            this.dNUCID = dnuc.nameENSDF();
            if (AMEConfig.getMass(this.dNUCID) == null) {
                if (this.dNUCID.length() > 0) {
                    JOptionPane.showMessageDialog(this, "Error: wrong decay mode: daughter nuclide " + this.dNUCID + " does not exist!");
                }
                return;
            }
            SDS2XDX T12 = null;
            SDS2XDX QV = null;
            SDS2XDX EP = null;
            SDS2XDX EL = null;
            SDS2XDX TI = null;
            SDS2XDX LOGFT = null;
            String s = "";
            String dm = "";
            String line = "";
            String pLine = "";
            String normLine = "";
            String levLine = "";
            String decayLine = "";
            prefix = Str.makeENSDFLinePrefix(this.dNUCID, "   ");
            dm = this.isBetaMinus ? "B-" : "EC";
            line = String.valueOf(prefix) + this.pNUCID + " " + dm + " DECAY";
            linesV.add(line);
            try {
                s = "";
                LOGFT = null;
                EP = this.convertToENSDF(this.eps, this.deps);
                T12 = this.convertToENSDF(this.ts, this.dts);
                QV = this.convertToENSDF(this.qs, this.dqs);
                EL = this.convertToENSDF(this.els, this.dels);
                TI = this.convertToENSDF(this.tis, this.dtis);
                LOGFT = this.convertToENSDF(this.logfts, this.dlogfts);
                s = "";
                String s1 = "";
                String s2 = "";
                if (this.eps.isEmpty()) {
                    s = String.valueOf(s) + ", E(parent)";
                } else if (EP == null || EP.x() < 0.0) {
                    s1 = String.valueOf(s1) + ", E(parent)";
                } else if (this.deps.contains(".")) {
                    s2 = String.valueOf(s2) + ", E(parent)";
                }
                if (this.ts.isEmpty()) {
                    s = String.valueOf(s) + "parent T1/2";
                } else if (T12 == null || T12.x() <= 0.0) {
                    s1 = String.valueOf(s1) + "parent T1/2";
                } else if (this.dts.contains(".")) {
                    s2 = String.valueOf(s2) + "parent T1/2";
                }
                if (this.qs.isEmpty()) {
                    s = String.valueOf(s) + ", Q-value";
                } else if (QV == null || QV.x() <= 0.0) {
                    s1 = String.valueOf(s1) + ", Q-value";
                } else {
                    isUseQVFromAME = false;
                    if (this.dqs.contains(".")) {
                        s2 = String.valueOf(s2) + ", Q-value";
                    }
                }
                if (this.els.isEmpty()) {
                    s = String.valueOf(s) + ", E(level)";
                } else if (EL == null || EL.x() < 0.0) {
                    s1 = String.valueOf(s1) + ", E(level)";
                } else if (this.dels.contains(".")) {
                    s2 = String.valueOf(s2) + ", E(level)";
                }
                if (!this.isCalculateBR) {
                    if (this.tis.isEmpty()) {
                        s = String.valueOf(s) + ", %I(total)";
                    } else if (TI == null || TI.x() <= 0.0) {
                        s1 = String.valueOf(s1) + ", %I(total)";
                    } else if (this.dtis.contains(".")) {
                        s2 = String.valueOf(s2) + ", %I(total)";
                    }
                }
                if (s.startsWith(",")) {
                    s = s.substring(1).trim();
                }
                if (s1.startsWith(",")) {
                    s1 = s1.substring(1).trim();
                }
                if (s2.startsWith(",")) {
                    s2 = s2.substring(1).trim();
                }
                if (s.equals("Q-value")) {
                    s = "";
                    isUseQVFromAME = true;
                } else {
                    s = s.replace("Q-value,", "");
                }
                if (!(s.isEmpty() && s1.isEmpty() && s2.isEmpty())) {
                    if (!s.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "Error: " + s + " not specified.");
                        return;
                    }
                    if (!s1.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "Error: wrong input for " + s1);
                        return;
                    }
                    if (!s2.isEmpty() && this.isENSDFStyleUnc) {
                        JOptionPane.showMessageDialog(this, "Error: ENSDF-style uncertainty is expected but real-value is given in\n  " + s2);
                        return;
                    }
                }
                prefix = Str.makeENSDFLinePrefix(this.pNUCID, "  P");
                line = String.valueOf(prefix) + Str.repeat(" ", 71);
                String pCRLine = "";
                if (EP != null) {
                    pLine = EnsdfUtil.replaceEnergyOfRecordLine(line, "", "");
                    if (EP.s().length() <= 10 && EP.ds().length() <= 2) {
                        pLine = EnsdfUtil.replaceEnergyOfRecordLine(line, EP.s(), EP.ds());
                    } else {
                        pCRLine = "E=" + EL.s() + " " + EL.ds();
                        pCRLine = pCRLine.trim();
                    }
                }
                if (this.jps.length() > 0) {
                    pLine = EnsdfUtil.replaceJPIOfLevelLine(pLine, this.jps);
                }
                if (T12 != null) {
                    int n = T12.s().length() + this.tunit.length() + 1;
                    if (n <= 10 && T12.ds().length() <= 6) {
                        pLine = EnsdfUtil.replaceHalflifeOfLevelLine(pLine, T12.s(), T12.ds(), this.tunit);
                    } else {
                        pCRLine = pCRLine.isEmpty() ? "T=" + T12.s() + " " + this.tunit + " " + T12.ds() : String.valueOf(pCRLine) + "$T=" + T12.s() + " " + this.tunit + " " + T12.ds();
                        pCRLine = pCRLine.trim();
                    }
                }
                if (isUseQVFromAME) {
                    QV = this.isBetaMinus ? AMEConfig.getQBFromAME(this.pNUCID) : AMEConfig.getQECFromAME(this.pNUCID);
                }
                if (QV != null && pLine.length() > 0) {
                    if (QV.s().length() <= 10 && QV.ds().length() <= 2) {
                        pLine = EnsdfUtil.replaceC2SOfLevelLine(pLine, QV.s(), QV.ds());
                    } else {
                        pCRLine = pCRLine.isEmpty() ? "QP=" + QV.s() + " " + QV.ds() : String.valueOf(pCRLine) + "$QP=" + QV.s() + " " + QV.ds();
                        pCRLine = pCRLine.trim();
                    }
                }
                if (!pCRLine.isEmpty()) {
                    prefix = Str.makeENSDFLinePrefix(this.pNUCID, "2 P");
                    pCRLine = String.valueOf(prefix) + pCRLine;
                }
                if (pLine.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Error: something wrong with inputs for parent nuclide");
                    return;
                }
                prefix = Str.makeENSDFLinePrefix(this.dNUCID, "  N");
                line = String.valueOf(prefix) + Str.repeat(" ", 71);
                String normCRLine = "";
                String brs = "100";
                String dbrs = "";
                SDS2XDX BR = this.convertToENSDF(brs, dbrs);
                if (BR != null) {
                    normLine = EnsdfUtil.replaceEnergyOfRecordLine(line, "", "");
                    if ((BR = BR.divided(100.0f)).s().length() <= 8 && BR.ds().length() <= 2) {
                        normLine = EnsdfUtil.replaceIEOfECBPLine(normLine, BR.s(), BR.ds());
                    } else {
                        normCRLine = "BR=" + BR.s() + " " + BR.ds();
                        normCRLine = normCRLine.trim();
                    }
                }
                if (!normCRLine.isEmpty()) {
                    prefix = Str.makeENSDFLinePrefix(this.dNUCID, "2 N");
                    normCRLine = String.valueOf(prefix) + normCRLine;
                }
                prefix = Str.makeENSDFLinePrefix(this.dNUCID, "  L");
                line = String.valueOf(prefix) + Str.repeat(" ", 71);
                String levCRLine = "";
                if (EL != null) {
                    levLine = EnsdfUtil.replaceEnergyOfRecordLine(line, "", "");
                    if (EL.s().length() <= 10 && EL.ds().length() <= 2) {
                        levLine = EnsdfUtil.replaceEnergyOfRecordLine(levLine, EL.s(), EL.ds());
                    } else {
                        levCRLine = "E=" + EL.s() + " " + EL.ds();
                        levCRLine = levCRLine.trim();
                    }
                }
                if (this.jfs.length() > 0 && !this.useSelectedUniqueness) {
                    levLine = EnsdfUtil.replaceJPIOfLevelLine(levLine, this.jfs);
                }
                if (!levCRLine.isEmpty()) {
                    prefix = Str.makeENSDFLinePrefix(this.dNUCID, "2 L");
                    levCRLine = String.valueOf(prefix) + levCRLine;
                }
                prefix = Str.makeENSDFLinePrefix(this.dNUCID, "  " + dm.charAt(0));
                line = String.valueOf(prefix) + Str.repeat(" ", 71);
                String decayCRLine = "";
                if (TI != null && !this.isCalculateBR) {
                    decayLine = EnsdfUtil.replaceEnergyOfRecordLine(line, "", "");
                    if (TI.s().length() <= 8 && TI.ds().length() <= 2) {
                        decayLine = this.isBetaMinus ? EnsdfUtil.replaceRIOfRecordLine(decayLine, TI.s(), TI.ds()) : EnsdfUtil.replaceTIOfGammaLine(decayLine, TI.s(), TI.ds());
                    } else {
                        decayCRLine = this.isBetaMinus ? "IB=" + TI.s() + " " + TI.ds() : "TI=" + TI.s() + " " + TI.ds();
                        decayCRLine = decayCRLine.trim();
                    }
                } else if (LOGFT != null && this.isCalculateBR) {
                    decayLine = EnsdfUtil.replaceEnergyOfRecordLine(line, "", "");
                    if (LOGFT.s().length() <= 8 && LOGFT.ds().length() <= 2) {
                        decayLine = EnsdfUtil.replaceLOGFTOfBetaLine(decayLine, LOGFT.s(), LOGFT.ds());
                    } else {
                        decayCRLine = "LOGFT=" + LOGFT.s() + " " + LOGFT.ds();
                        decayCRLine = decayCRLine.trim();
                    }
                }
                if (!this.un.isEmpty() && this.un.length() == 2) {
                    decayLine = Str.fixLineLength(decayLine, 80);
                    decayLine = String.valueOf(decayLine.substring(0, 77)) + this.un + decayLine.substring(79);
                }
                if (!decayCRLine.isEmpty()) {
                    prefix = Str.makeENSDFLinePrefix(this.dNUCID, "2 " + dm.charAt(0));
                    decayCRLine = String.valueOf(prefix) + decayCRLine;
                }
                linesV.add(pLine);
                if (pCRLine.length() > 0) {
                    linesV.add(pCRLine);
                }
                linesV.add(normLine);
                if (normCRLine.length() > 0) {
                    linesV.add(normCRLine);
                }
                if (levLine.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Error: something wrong with inputs for E(level)");
                    return;
                }
                linesV.add(levLine);
                if (levCRLine.length() > 0) {
                    linesV.add(levCRLine);
                }
                if (decayLine.isEmpty()) {
                    String msg = "";
                    msg = this.isCalculateBR ? (this.logfts.isEmpty() ? "Error: input logft is needed but empty" : "Error: something wrong with inputs for logft") : (this.tis.isEmpty() ? "Error: input %I(total) is needed but empty" : "Error: something wrong with inputs for %I(total)");
                    JOptionPane.showMessageDialog(this, msg);
                    return;
                }
                linesV.add(decayLine);
                if (decayCRLine.length() > 0) {
                    linesV.add(decayCRLine);
                }
                this.ens = new ENSDF();
                RADControl.calculateBremsstrahlung = false;
                this.isLoaded = true;
                String htmlPnucid = "<sup>" + pnuc.A() + "</sup>" + pnuc.En() + "<sup>" + pnuc.Z() + "</sup>";
                String htmlDnucid = "<sup>" + dnuc.A() + "</sup>" + dnuc.En() + "<sup>" + dnuc.Z() + "</sup>";
                s1 = "";
                if (isUseQVFromAME) {
                    if (QV != null && QV.x() > 0.0) {
                        s1 = QV.s();
                        if (!QV.ds().isEmpty() && QV.dxu() > 0.0) {
                            s1 = String.valueOf(s1) + "(" + QV.ds() + ")";
                        }
                    }
                    if (s1.length() > 0) {
                        s1 = "using Q-value=" + s1 + " from AME2020";
                    }
                }
                String dm1 = dm;
                if (!this.isBetaMinus) {
                    dm1 = "EC/B+";
                }
                String result = "For " + htmlPnucid + " " + dm1 + " decay to " + htmlDnucid;
                if (s1.length() > 0) {
                    result = String.valueOf(result) + "<br>" + s1;
                }
                if (this.isCalculateBR) {
                    int index = linesV.indexOf(decayLine);
                    decayLine = this.isBetaMinus ? EnsdfUtil.replaceRIOfRecordLine(decayLine, "50", "0") : EnsdfUtil.replaceTIOfGammaLine(decayLine, "50", "0");
                    linesV.remove(index);
                    linesV.add(index, decayLine);
                    this.ens.setValues(linesV);
                    this.dataset = new DecayDataset(this.ens);
                    this.spec = this.dataset.spectrumAt(0);
                    double f = this.spec.fTotal().x;
                    double dfl = this.spec.fTotal().dxl();
                    double dfu = this.spec.fTotal().dxu();
                    SDS2XDX F = new SDS2XDX();
                    F.setErrorLimit(99);
                    F.setValues(f, dfu, dfl);
                    double logft = LOGFT.x();
                    double dlogftL = LOGFT.dxl();
                    double dlogftU = LOGFT.dxu();
                    if (dlogftL < 0.0) {
                        dlogftL = 0.0;
                    }
                    if (dlogftU < 0.0) {
                        dlogftU = 0.0;
                    }
                    BR = T12.multiply(F);
                    BR = BR.multiply(EnsdfUtil.T12UnitMultiplier(this.tunit));
                    double x = Math.pow(10.0, logft);
                    double dxu = Math.pow(10.0, logft + dlogftU) - x;
                    double dxl = x - Math.pow(10.0, logft - dlogftL);
                    SDS2XDX sx = new SDS2XDX();
                    sx.setErrorLimit(99);
                    if (LOGFT.dxl() >= 0.0) {
                        sx.setValues(x, dxu, dxl);
                    } else {
                        sx.setValues(x, -1.0, -1);
                        sx.setDS(LOGFT.ds());
                    }
                    BR = BR.divided(sx);
                    BR = BR.multiply(100.0f);
                    if (BR == null || BR.x() <= 0.0) {
                        s = "<HTML>can't get branching for " + htmlDnucid + "</HTML>";
                        break block120;
                    }
                    s = BR.s();
                    if (!BR.ds().isEmpty()) {
                        if (BR.dxu() > 0.0) {
                            s = "=" + s + "(" + BR.ds() + ")";
                        } else {
                            String ds1 = EnsdfConfig.convertEnsdfOP2MathOP(BR.ds());
                            s = (ds1 = ds1.replace("<=", "&le;").replace(">=", "&ge;").replace("<", "&lt;").replace(">", "&gt;")).length() > 0 && !Str.isLetters(ds1) ? String.valueOf(ds1) + s : "=" + s + "(" + BR.ds() + ")";
                        }
                    } else {
                        s = "=" + s;
                    }
                    result = String.valueOf(result) + "<br>*** branching %I(total) " + s;
                    result = "<HTML>" + result + "</HTML>";
                    this.resultLabel.setText(result);
                    String tempS = BR.s();
                    String tempDS = BR.ds();
                    boolean setContBR = false;
                    if (BR.ds().length() > 2) {
                        tempS = "";
                        tempDS = "";
                        setContBR = true;
                    }
                    decayCRLine = "";
                    index = linesV.indexOf(decayLine);
                    if (this.isBetaMinus) {
                        decayLine = EnsdfUtil.replaceRIOfRecordLine(decayLine, tempS, tempDS);
                        if (setContBR) {
                            decayCRLine = "IB=" + BR.s() + " " + BR.ds();
                        }
                    } else {
                        decayLine = EnsdfUtil.replaceTIOfGammaLine(decayLine, tempS, tempDS);
                        if (setContBR) {
                            decayCRLine = "TI=" + BR.s() + " " + BR.ds();
                        }
                    }
                    linesV.remove(index);
                    linesV.add(index, decayLine);
                    if (setContBR) {
                        decayCRLine = decayCRLine.trim();
                        prefix = Str.makeENSDFLinePrefix(this.dNUCID, "2 " + dm.charAt(0));
                        decayCRLine = String.valueOf(prefix) + decayCRLine;
                        linesV.add(index + 1, decayCRLine);
                    }
                    this.ens = new ENSDF();
                    this.ens.setValues(linesV);
                    this.dataset = new DecayDataset(this.ens);
                    this.spec = this.dataset.spectrumAt(0);
                    this.updateLogftResultFrame();
                    return;
                }
                this.ens.setValues(linesV);
                this.dataset = new DecayDataset(this.ens);
                this.spec = this.dataset.spectrumAt(0);
                double x = this.spec.logft().x();
                double dxl = this.spec.logft().dxl();
                double dxu = this.spec.logft().dxu();
                if (x <= 0.0) {
                    JOptionPane.showMessageDialog(this, "Error: negative logft=" + String.format("%.2f", x) + " Check if input T1/2 unit=" + this.tunit + " is correct.");
                }
                LOGFT = new SDS2XDX();
                LOGFT.setErrorLimit(99);
                LOGFT.setValues(x, dxu, dxl);
                if (LOGFT == null || LOGFT.x() <= 0.0) {
                    s = "<HTML>can't get logft for " + htmlDnucid + "</HTML>";
                    break block120;
                }
                s = LOGFT.s();
                if (!LOGFT.ds().isEmpty() && LOGFT.dxu() > 0.0) {
                    s = String.valueOf(s) + "(" + LOGFT.ds() + ")";
                }
                result = String.valueOf(result) + "<br>*** logft=" + s;
                if (this.spec.nForbiddenUnique() == 0) {
                    DecaySpectrum spec1 = this.spec.makeSpectrum(1, 1);
                    x = spec1.logft().x();
                    dxl = spec1.logft().dxl();
                    dxu = spec1.logft().dxu();
                    SDS2XDX LOGFT1 = new SDS2XDX();
                    LOGFT1.setErrorLimit(99);
                    LOGFT1.setValues(x, dxu, dxl);
                    s = LOGFT1.s();
                    if (!LOGFT1.ds().isEmpty() && LOGFT1.dxu() > 0.0) {
                        s = String.valueOf(s) + "(" + LOGFT1.ds() + ")";
                    }
                    result = String.valueOf(result) + "<br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;=" + s + " (calculated as 1st Unique)";
                }
                if ((s = this.printXDXInENSDF(this.spec.averageEnergy())).length() > 0) {
                    result = String.valueOf(result) + "<br>*** average decay energy=" + s;
                }
                if (!this.isBetaMinus) {
                    s1 = this.printXDXInENSDF(this.spec.calcIBeta());
                    s = "";
                    if (s1.length() > 0) {
                        s = "%I(&beta<sup>+</sup>)=" + s1;
                    }
                    if ((s1 = this.printXDXInENSDF(this.spec.calcIEC())).length() > 0) {
                        s = s.length() > 0 ? String.valueOf(s) + "&nbsp;&nbsp;&nbsp;%I(&epsilon)=" + s1 : "%I(&epsilon)=" + s1;
                    }
                    if (s.length() > 0) {
                        result = String.valueOf(result) + "<br>*** " + s;
                    }
                }
                result = "<HTML>" + result + "</HTML>";
                this.resultLabel.setText(result);
                this.updateLogftResultFrame();
                return;
            }
            catch (Exception e1) {
                e1.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error: something wrong when calculating logft or branching");
            }
        }
    }

    private String printXDXInENSDF(XDX xdx) {
        String s = "";
        SDS2XDX s2x = new SDS2XDX();
        if (xdx.x() > 0.0) {
            s2x.setErrorLimit(99);
            s2x.setValues(xdx.x(), xdx.dxu(), xdx.dxl());
            s = s2x.s();
            if (!s2x.ds().isEmpty() && s2x.dxu() > 0.0) {
                s = String.valueOf(s) + "(" + s2x.ds() + ")";
            }
        }
        return s;
    }

    private String printDetailedResults(DecaySpectrum spec, ENSDF ens) {
        try {
            Decay d0;
            boolean isECBP;
            StringBuilder temp = new StringBuilder();
            String indent = "  ";
            String betaType = "B-";
            Parent parent = ens.parentAt(0);
            Decay d = spec.getDecay();
            int errorLimit = RADControl.errorLimit;
            String IBS = "";
            String DIBS = "";
            String IES = "";
            String DIES = "";
            String ITS = "";
            String DITS = "";
            String LOGFTS = "";
            String DLOGFTS = "";
            String s = "";
            String[] out = null;
            String tempDS = "";
            Level lev = ens.levelAt(d.getFLI());
            boolean bl = isECBP = !this.isBetaMinus;
            if (!this.isBetaMinus) {
                betaType = "B+";
                int maxErrorLimit = 0;
                int dibLimit = 0;
                int dieLimit = 0;
                int ditLimit = 0;
                if (Str.isInteger(d.DRIS()) && (dibLimit = d.DRIS().length() == 2 ? Integer.parseInt(d.DRIS()) : (d.DRIS().length() == 1 ? 9 : RADControl.errorLimit)) > maxErrorLimit) {
                    maxErrorLimit = dibLimit;
                }
                if (Str.isInteger(d.DIES()) && (dieLimit = d.DIES().length() == 2 ? Integer.parseInt(d.DIES()) : (d.DIES().length() == 1 ? 9 : RADControl.errorLimit)) > maxErrorLimit) {
                    maxErrorLimit = dieLimit;
                }
                if (Str.isInteger(d.DITS())) {
                    ditLimit = d.DITS().length() == 2 ? Integer.parseInt(d.DITS()) : (d.DITS().length() == 1 ? 9 : RADControl.errorLimit);
                    if (ditLimit > maxErrorLimit) {
                        maxErrorLimit = ditLimit;
                    }
                    dieLimit = ditLimit;
                    dibLimit = ditLimit;
                } else if (d.ITS().isEmpty()) {
                    SDS2XDX IE;
                    SDS2XDX IB = new SDS2XDX(d.RIS(), d.DRIS());
                    SDS2XDX IT = IB.add(IE = new SDS2XDX(d.IES(), d.DIES()), true);
                    String ds = IT.ds();
                    if (Str.isInteger(ds)) {
                        ditLimit = ds.length() == 2 ? Integer.parseInt(ds) : (ds.length() == 1 ? 9 : RADControl.errorLimit);
                    }
                    if (ditLimit < maxErrorLimit) {
                        ditLimit = maxErrorLimit;
                    }
                }
                if (maxErrorLimit > RADControl.errorLimit) {
                    maxErrorLimit = RADControl.errorLimit;
                }
                if (dibLimit <= 0) {
                    dibLimit = maxErrorLimit;
                }
                if (dieLimit <= 0) {
                    dieLimit = maxErrorLimit;
                }
                if (ditLimit <= 0) {
                    ditLimit = maxErrorLimit;
                }
                if (spec.calcIBeta().x >= 0.001 || spec.calcIBeta().x >= 0.001 * spec.decayIT().x) {
                    out = this.makeSDS(spec.calcIBeta(), dibLimit);
                    IBS = out[0];
                    DIBS = out[1];
                }
                if (spec.calcIEC().x >= 1.0E-4 || spec.calcIEC().x >= 0.001 * spec.decayIT().x) {
                    out = this.makeSDS(spec.calcIEC(), dieLimit);
                    IES = out[0];
                    DIES = out[1];
                }
                if (d.ITS().length() == 0) {
                    out = this.makeSDS(spec.decayIT(), ditLimit);
                    ITS = out[0];
                    DITS = out[1];
                }
                tempDS = String.valueOf(d.DIES()) + d.DRIS() + d.DITS();
            } else {
                tempDS = d.DRIS();
            }
            errorLimit = 6;
            out = this.makeSDS(spec.logft(), errorLimit);
            LOGFTS = out[0];
            DLOGFTS = out[1];
            SDS2XDX s2x = new SDS2XDX(LOGFTS, DLOGFTS);
            if (s2x.x() > 0.0) {
                double x = spec.logft().x;
                double dxu = spec.logft().dxu;
                double dxl = spec.logft().dxl;
                if (s2x.x() == s2x.dxl() && x > dxl && dxl >= 0.0 && dxu >= 0.0) {
                    XDX2SDS x2s = new XDX2SDS(x, dxu, dxl, 35);
                    if (!x2s.dsl().equals(x2s.s())) {
                        LOGFTS = x2s.s();
                        DLOGFTS = x2s.ds();
                    }
                } else if (Str.isInteger(LOGFTS) && LOGFTS.length() == 1 && s2x.dsu().length() == 1 && s2x.dsl().length() == 1) {
                    out = this.makeSDS(spec.logft(), 25);
                    LOGFTS = out[0];
                    DLOGFTS = out[1];
                }
            }
            if (!Str.isNumeric(DLOGFTS) && !DLOGFTS.contains("+")) {
                LOGFTS = Str.roundToNDigitsAfterDot(LOGFTS, 1);
            } else if (RADControl.suppressLogftUncertainty) {
                LOGFTS = Str.roundToNDigitsAfterDot(LOGFTS, 1);
                DLOGFTS = "";
            } else if (tempDS.trim().isEmpty() && !(Math.abs(spec.decayBR().x - 1.0) < 0.001)) {
                LOGFTS = Str.roundToNDigitsAfterDot(LOGFTS, 1);
                DLOGFTS = "";
            }
            String crLine = "";
            String crLine1 = "";
            int k = 0;
            while (k < d.nContRecordLines()) {
                String line = d.contRecordLineAt(k);
                if (line.contains("EAV")) {
                    crLine = line;
                    break;
                }
                if (isECBP && line.contains("CK=")) {
                    crLine1 = line;
                }
                ++k;
            }
            if (crLine.isEmpty() && !crLine1.isEmpty()) {
                crLine = crLine1;
            }
            String tempLine = "";
            XDX eav = spec.averageEnergy();
            if (eav.x < 0.0) {
                eav = XDX.ZERO(-1.0);
            }
            out = this.makeSDS(eav);
            s = String.valueOf(out[0].trim()) + " " + out[1].trim();
            if ((s = s.trim()).length() > 0 && eav.x() > 0.0) {
                tempLine = String.valueOf(tempLine) + "EAV=" + s + "$";
            }
            if (isECBP) {
                XDX[] ratios = new XDX[]{spec.ratioK2ECBP(), spec.ratioL2ECBP(), spec.ratioMNO2ECBP()};
                String[] names = new String[]{"CK", "CL", "CM+"};
                int k2 = 0;
                while (k2 < ratios.length) {
                    out = this.makeSDS(ratios[k2]);
                    s = String.valueOf(out[0].trim()) + " " + out[1].trim();
                    if ((s = s.trim()).length() > 0 && ratios[k2].x() > 0.0) {
                        tempLine = String.valueOf(tempLine) + names[k2] + "=" + s + "$";
                    }
                    ++k2;
                }
            }
            XDX levelE = null;
            levelE = lev.EF() == 0.0f ? new XDX(0.0, 0.0) : new XDX(lev.EF(), lev.DEF());
            levelE.dsl = lev.DES();
            levelE.dsu = lev.DES();
            XDX totalDecayE = new XDX(parent.QF(), parent.DQF());
            totalDecayE.dsl = parent.DQS();
            totalDecayE.dsu = parent.DQS();
            totalDecayE.checkSDS();
            XDX parentE = null;
            parentE = parent.level().EF() == 0.0f ? new XDX(0.0, 0.0) : new XDX(parent.level().EF(), parent.level().DEF());
            totalDecayE = totalDecayE.add(parentE);
            totalDecayE = totalDecayE.subtract(levelE);
            XDX EBMax = null;
            if (isECBP) {
                d0 = (ECBP)d;
                EBMax = new XDX(((ECBP)d0).EPEF_BP(), ((ECBP)d0).DEPEF_BP());
                EBMax.dsl = ((ECBP)d0).DEPES_BP();
                EBMax.dsu = ((ECBP)d0).DEPES_BP();
            } else {
                d0 = (Beta)d;
                EBMax = new XDX(((Beta)d0).EF(), ((Beta)d0).DEF());
                EBMax.dsl = ((Beta)d0).DES();
                EBMax.dsu = ((Beta)d0).DES();
            }
            EBMax.checkSDS();
            String jis = spec.parent().level().JPiS();
            String jfs = spec.getDecay().getJFS();
            String label = "";
            label = spec.isUnique() ? "forbidden-unique=" + spec.nForbiddenUnique() : (spec.isAllowed() ? "allowed" : (spec.isNonUnique() ? (spec.nForbiddenUnique() > 0 ? "non-unique=" + spec.nForbidden() + " calculated as forbidden-unique=" + spec.nForbiddenUnique() : "non-unique=" + spec.nForbidden() + " calculated as allowed") : (spec.nForbiddenUnique() > 0 ? "calculated as forbidden-unique=" + spec.nForbiddenUnique() : "calculated as allowed")));
            temp.append(String.valueOf(indent) + "*** uncertainties of results are in ENSDF style ***\n");
            int FLI = spec.getDecay().getFLI();
            temp.append(String.valueOf(ens.levelAt(FLI).recordLine()) + "\n");
            temp.append(String.valueOf(indent) + "Parent JPI=" + jis + " " + "   final-level JPI=" + jfs + "  *" + label + "*\n\n");
            XDX BR = spec.decayBR().multiply(100.0);
            XDX PT = spec.partialT12();
            if (this.isCalculateBR) {
                SDS2XDX sx = spec.findFeedingfromLogft(d.LOGFTS(), d.DLOGFTS());
                BR = new XDX(sx.x(), sx.dxu(), sx.dxl());
                if (!Str.isNumeric(sx.ds()) && sx.dxu() < 0.0) {
                    BR.dsu = BR.dsl = sx.ds();
                }
                double f = spec.fTotal().x;
                double dfl = spec.fTotal().dxl();
                double dfu = spec.fTotal().dxu();
                SDS2XDX F = new SDS2XDX();
                F.setErrorLimit(99);
                F.setValues(f, dfu, dfl);
                SDS2XDX LOGFT = new SDS2XDX(d.LOGFTS(), d.DLOGFTS());
                LOGFT.setErrorLimit(99);
                double logft = LOGFT.x();
                double dlogftL = LOGFT.dxl();
                double dlogftU = LOGFT.dxu();
                if (dlogftL < 0.0) {
                    dlogftL = 0.0;
                }
                if (dlogftU < 0.0) {
                    dlogftU = 0.0;
                }
                double x = Math.pow(10.0, logft);
                double dxu = Math.pow(10.0, logft + dlogftU) - x;
                double dxl = x - Math.pow(10.0, logft - dlogftL);
                sx = new SDS2XDX();
                sx.setErrorLimit(99);
                if (LOGFT.dxl() >= 0.0) {
                    sx.setValues(x, dxu, dxl);
                } else {
                    sx.setValues(x, -1.0, -1);
                    sx.setDS(LOGFT.ds());
                }
                SDS2XDX T12 = sx.divided(F);
                PT = new XDX(T12.x(), T12.dxu(), T12.dxl());
                if (!Str.isNumeric(T12.ds())) {
                    PT.dsu = PT.dsl = T12.ds();
                }
            } else {
                temp.append(String.valueOf(indent) + "Total decay energy     =" + this.printXDX0(totalDecayE) + " BR(%)=" + this.printXDX0(BR) + " partial T1/2 (s) =" + this.printXDX0(spec.partialT12()) + "\n");
                temp.append(String.valueOf(indent) + "End-of-point(" + betaType + ") EMAX  =" + this.printXDX0(EBMax) + "\n");
                temp.append(String.valueOf(indent) + "Average energy(" + betaType + ") EAV =" + this.printXDX0(eav) + " EAV/EMAX=" + this.printXDX0(eav.divided(EBMax)) + "\n");
            }
            if (!this.isBetaMinus) {
                String logEC2BP_s = this.printXDX0(XDX.log10(spec.ratioEC2BP()));
                String ECK2BP_s = this.printXDX0(spec.fK().divided(spec.fB()));
                temp.append("\n");
                temp.append(String.valueOf(indent) + String.format("EC/B+       =%s log(EC/B+)  =%s ECK/B+         =%s\n", this.printXDX0(spec.ratioEC2BP()), logEC2BP_s, ECK2BP_s));
                temp.append(String.valueOf(indent) + String.format("ECK/(EC+B+) =%s ECL/(EC+B+) =%s EC(M+)/(EC+B+) =%s\n", this.printXDX0(spec.ratioK2ECBP()), this.printXDX0(spec.ratioL2ECBP()), this.printXDX0(spec.ratioMNO2ECBP())));
                if (!this.isCalculateBR) {
                    temp.append(String.valueOf(indent) + String.format("I(B+)       =%s I(EC)       =%s\n", this.printXDX0(spec.calcIBeta()), this.printXDX0(spec.calcIEC())));
                }
                temp.append("\n");
            }
            try {
                XDX pt;
                if (this.isCalculateBR) {
                    temp.append(String.valueOf(indent) + "** input logft      = " + XDX.printSDS(d.LOGFTS(), d.DLOGFTS(), 12, "LEFT") + "\n");
                    temp.append(String.valueOf(indent) + "** partial T1/2 (s) = " + this.printXDX0(PT) + "\n");
                    temp.append(String.valueOf(indent) + "** calculated BR(%) = " + this.printXDX0(BR) + "\n");
                } else if (spec.isUnique()) {
                    pt = spec.partialT12();
                    XDX f1 = spec.fTotal();
                    XDX f1t = spec.ft();
                    XDX logf1t = spec.logft();
                    XDX f0 = spec.spec0().fTotal();
                    XDX logf0t = spec.spec0().logft();
                    XDX f1Overf0 = f1.divided(f0);
                    temp.append(String.valueOf(indent) + String.format("  log(t)    =%s     log(f1) =%s   log(f1/f0) =%s\n", this.printXDX0(XDX.log10(pt)), this.printXDX0(XDX.log10(f1)), this.printXDX0(XDX.log10(f1Overf0))));
                    temp.append(String.valueOf(indent) + String.format("  log(f1*t) =%s     f1*t    =%s   log(f0*t)  =%s\n", this.printXDX0(logf1t), this.printXDX0(f1t), this.printXDX0(logf0t)));
                } else {
                    pt = spec.partialT12();
                    XDX f0 = spec.fTotal();
                    XDX f0t = spec.ft();
                    XDX logf0t = spec.logft();
                    temp.append(String.valueOf(indent) + String.format("  log(t)    =%s       log(f0) =%s\n", this.printXDX0(XDX.log10(pt)), this.printXDX0(XDX.log10(f0))));
                    temp.append(String.valueOf(indent) + String.format("  log(f0*t) =%s       f0*t    =%s\n", this.printXDX0(logf0t), this.printXDX0(f0t)));
                }
                if (spec.nForbiddenUnique() == 0 && !this.isCalculateBR) {
                    DecaySpectrum spec1 = null;
                    spec1 = spec.alternativeSpecsV().size() == 0 ? spec.makeSpectrum(1, 1) : spec.alternativeSpecsV().get(0);
                    if (spec1.nForbiddenUnique() == 1) {
                        XDX logf1t = spec1.logft();
                        temp.append("\n");
                        temp.append(String.valueOf(indent) + String.format("  log(f1*t) =%s  (calculated as 1st forbidden unique)\n", this.printXDX0(logf1t)));
                    }
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
            if (!this.isCalculateBR) {
                temp.append(this.printRelevantLogftSystematics(spec, ens, indent));
            }
            return temp.toString();
        }
        catch (Exception exception) {
            return "";
        }
    }

    public String printRelevantLogftSystematics(DecaySpectrum spec, ENSDF ens, String indent) {
        StringBuilder temp = new StringBuilder();
        Vector<LogftCategory> tempV = null;
        Vector<LogftCategory> tempV1 = null;
        int n = spec.nForbiddenUnique();
        Nucleus nuc = null;
        if (ens.nParents() > 0) {
            nuc = ens.parentAt(0).nucleus();
            int A = nuc.A();
            int Z = nuc.Z();
            temp.append("\n");
            if (n == 0) {
                tempV = LogftSystematics.findCategoriesByCriteria("0,1", "", A, Z);
                tempV1 = LogftSystematics.findCategoriesByCriteria("2", "", A, Z);
            } else if (n == 1) {
                tempV = LogftSystematics.findCategoriesByCriteria("2", "", A, Z);
            } else if (n == 2) {
                tempV = LogftSystematics.findCategoriesByCriteria("3", "", A, Z);
            }
            String s = "";
            if (tempV != null && tempV.size() > 0) {
                double x = spec.logft().x;
                double dxu = spec.logft().dxu;
                double dxl = spec.logft().dxl;
                Vector<LogftCategory> lcsV = LogftSystematics.findPossibleCategories(x, dxu, dxl, tempV);
                for (LogftCategory lc : tempV) {
                    String ZS = "";
                    String markS = "";
                    if (lc.ZLabel().length() > 0) {
                        ZS = "Z" + lc.ZLabel();
                    }
                    if (lcsV.contains(lc)) {
                        markS = "***";
                    }
                    s = String.valueOf(s) + indent + String.format(" %30s DJ=%-2d DPI=%-4s %-6s logft=%-6.2f to %-6.2f%s\n", lc.name(), lc.DJ(), lc.DP(), ZS, lc.min(), lc.max(), markS);
                }
            }
            if (tempV1 != null && tempV1.size() > 0 && spec.alternativeSpecsV().size() > 0) {
                DecaySpectrum spec1 = spec.alternativeSpecsV().get(0);
                double x = spec1.logft().x;
                double dxu = spec1.logft().dxu;
                double dxl = spec1.logft().dxl;
                Vector<LogftCategory> lcsV = LogftSystematics.findPossibleCategories(x, dxu, dxl, tempV1);
                for (LogftCategory lc : tempV1) {
                    String ZS = "";
                    String markS = "";
                    if (lc.ZLabel().length() > 0) {
                        ZS = "Z" + lc.ZLabel();
                    }
                    if (lcsV.contains(lc)) {
                        markS = "***";
                    }
                    s = String.valueOf(s) + indent + String.format(" %30s DJ=%-2d DPI=%-4s %-6s logft=%-6.2f to %-6.2f%s\n", lc.name(), lc.DJ(), lc.DP(), ZS, lc.min(), lc.max(), markS);
                }
            }
            if (s.length() > 0) {
                temp.append(String.valueOf(indent) + "Systematics of logft values (2023TU02):\n");
                temp.append(s);
                temp.append("\n" + indent + "*** indicates possible decay type based on calculated logft\n");
            }
        }
        return temp.toString();
    }

    public String[] makeSDS(XDX xdx) {
        return this.makeSDS(xdx, RADControl.errorLimit);
    }

    public String[] makeSDS(XDX xdx, int errorLimit) {
        return XDX.makeSDS(xdx);
    }

    private String printXDX0(XDX xdx) {
        if (xdx == null || xdx.x == 0.0 && xdx.dxl <= 0.0 && xdx.dxu <= 0.0) {
            return XDX.printSDS(" ***", "", 10, 6, "LEFT");
        }
        return XDX.printXDX(xdx, 10, 6, "LEFT");
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
        }
        catch (Exception e1) {
            try {
                UIManager.LookAndFeelInfo[] lookAndFeelInfoArray = UIManager.getInstalledLookAndFeels();
                int n = lookAndFeelInfoArray.length;
                int n2 = 0;
                while (n2 < n) {
                    UIManager.LookAndFeelInfo info = lookAndFeelInfoArray[n2];
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                    }
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    ++n2;
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        EventQueue.invokeLater(new Runnable(){

            @Override
            public void run() {
                SimpleLogftCalculatorFrame frame = new SimpleLogftCalculatorFrame();
                frame.setVisible(true);
            }
        });
    }
}
