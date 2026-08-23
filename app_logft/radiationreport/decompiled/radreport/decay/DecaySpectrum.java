/*
 * Decompiled with CFR 0.152.
 */
package radreport.decay;

import ensdfparser.calc.UncertaintyParser;
import ensdfparser.calc.XDX;
import ensdfparser.ensdf.Beta;
import ensdfparser.ensdf.Decay;
import ensdfparser.ensdf.ECBP;
import ensdfparser.ensdf.JPI;
import ensdfparser.ensdf.Level;
import ensdfparser.ensdf.Nucleus;
import ensdfparser.ensdf.Parent;
import ensdfparser.ensdf.SDS2XDX;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.util.Str;
import java.util.Arrays;
import java.util.Vector;
import org.apache.commons.math3.complex.Complex;
import radreport.data.WaveFunctionEntry;
import radreport.decay.DataPoint2D;
import radreport.decay.RADConfig;
import radreport.decay.RADControl;
import radreport.math.Function1D;
import radreport.math.Integration;
import radreport.math.Util;

public class DecaySpectrum {
    private String name = "";
    private Vector<DataPoint2D> data = new Vector();
    private Vector<DataPoint2D> calculatedFermiV = new Vector();
    private Vector<DataPoint2D> calculatedIBStrenFuncV = new Vector();
    private Vector<DecaySpectrum> alternativeSpecsV = new Vector();
    private XDX averageTotalEnergy = XDX.ZERO();
    private XDX totalArea = XDX.ZERO();
    private float[] gridE = new float[0];
    private double[] E = new double[0];
    private XDX[] area = new XDX[0];
    private XDX[] Earea = new XDX[0];
    private XDX[] EareaInkeV = new XDX[0];
    private float[] gridEIB = new float[0];
    private double[] EIB = new double[0];
    private XDX[] areaIBbeta = new XDX[0];
    private XDX[] EareaIBbeta = new XDX[0];
    private XDX[] EareaIBbetaInkeV = new XDX[0];
    private XDX[] areaIBec = new XDX[0];
    private XDX[] EareaIBec = new XDX[0];
    private XDX[] EareaIBecInkeV = new XDX[0];
    private int A = 1;
    private int Z = -1;
    private int nForbiddenUnique = 0;
    private int nForbidden = 0;
    private int eZ = -1;
    private Nucleus nucleus = null;
    private Parent parent = null;
    private String NUCID = "";
    private String decayType = "";
    private double R = 0.0;
    private double W0 = 0.0;
    private double W0keV = 0.0;
    private double dW0keV = 0.0;
    private final double alpha = 0.007297735308;
    private final double me = 510.99906;
    private final double pi = Math.PI;
    private final int NCalcMin = 3;
    private final int NCalcMax = 200;
    private Function1D screeningCorrection = null;
    private Function1D nuclearSizeCorrFactor = null;
    private boolean isBetaM = false;
    private boolean isECBP = false;
    private boolean isUnique = false;
    private boolean isAllowed = true;
    private boolean isNonUnique = false;
    private JPI deltaJPI = null;
    int factor = 10;
    private double precisionRombBeta = 0.001;
    private double precisionFermiBeta = this.precisionRombBeta / (double)this.factor;
    private double precisionRombIB = 0.005;
    private double precisionFermiIB = this.precisionRombIB / (double)this.factor;
    private double precisionRombOpenIB = this.precisionRombIB / (double)this.factor;
    private double precisionIBStrenFunc = this.precisionRombIB / (double)this.factor;
    private double precisionIBfromEC = 0.001;
    private boolean calculateIBFromEC = true;
    private boolean hasCalculatedBeta = false;
    private boolean hasInputFeeding = true;
    private Decay decay = null;
    private double maxE1S = 0.0;
    private double maxE2S = 0.0;
    private double maxE2P = 0.0;
    private double maxE3P = 0.0;
    private double beK = 0.0;
    private double r_gL1gK_SQ = 0.0;
    private String indent = RADControl.indent;
    private String message = "";
    private Function1D Fermi = null;
    private Function1D wFermi = null;
    private XDX pT12 = XDX.ZERO(-1.0);
    private XDX fTotal = XDX.ZERO(-1.0);
    private XDX fB = XDX.ZERO(-1.0);
    private XDX fEC = XDX.ZERO(-1.0);
    private XDX fK = XDX.ZERO(-1.0);
    private XDX fL = XDX.ZERO(-1.0);
    private XDX fMNO = XDX.ZERO(-1.0);
    private XDX ft = XDX.ZERO(-1.0);
    private XDX logf = XDX.ZERO(-1.0);
    private XDX logft = XDX.ZERO(-1.0);
    private XDX rEC2BP = XDX.ZERO(-1.0);
    private XDX rK2ECBP = XDX.ZERO(-1.0);
    private XDX rL2ECBP = XDX.ZERO(-1.0);
    private XDX rMNO2ECBP = XDX.ZERO(-1.0);
    private XDX calcIBeta = XDX.ZERO(-1.0);
    private XDX calcIEC = XDX.ZERO(-1.0);
    private boolean hasIT = false;
    private boolean hasIB = false;
    private boolean hasIE = false;
    private XDX AIB = XDX.ZERO(-1.0);
    private XDX AIE = XDX.ZERO(-1.0);
    private XDX AIT = XDX.ZERO(-1.0);
    private XDX decayIT = XDX.ZERO(-1.0);
    private XDX decayBR = XDX.ZERO(-1.0);
    private DecaySpectrum spec0 = null;
    private boolean isRecursive = true;

    public DecaySpectrum(int Z0, int A0, double W0keV, String type, Parent parent, Decay decay) {
        this(Z0, A0, W0keV, 0.0, type, parent, decay);
    }

    public DecaySpectrum(int Z0, int A0, double W0keV, double dW0keV, String type, Parent parent, Decay decay) {
        this.nucleus = new Nucleus(Z0, A0);
        this.A = A0;
        this.Z = Z0;
        this.NUCID = this.nucleus.nameENSDF().trim();
        this.R = Util.radius(this.A);
        this.W0keV = W0keV;
        this.dW0keV = dW0keV;
        this.W0 = W0keV / 510.99906 + 1.0;
        this.decayType = type.toUpperCase().trim();
        if ("EC,B+,EC+B+".contains(this.decayType)) {
            this.eZ = 1;
            this.screeningCorrection = W -> DecaySpectrum.screeningCorrectionBetaP(this.Z, W);
            this.nuclearSizeCorrFactor = W -> DecaySpectrum.nuclearSizeCorrFactorBetaP(this.Z, W);
            this.isECBP = true;
        } else if (this.decayType.equals("B-")) {
            this.eZ = -1;
            this.screeningCorrection = W -> DecaySpectrum.screeningCorrectionBetaM(this.Z);
            this.nuclearSizeCorrFactor = W -> DecaySpectrum.nuclearSizeCorrFactorBetaM(this.Z, W);
            this.isBetaM = true;
        } else {
            this.screeningCorrection = W -> 0.0;
            this.nuclearSizeCorrFactor = W -> 0.0;
        }
        this.parent = parent;
        this.decay = decay;
        boolean hasSetUN = false;
        String un = decay.unique().trim().toUpperCase();
        if (un.length() == 2 && un.charAt(1) == 'U' && Str.isDigit(un.charAt(0))) {
            this.nForbiddenUnique = this.nForbidden = Integer.parseInt(String.valueOf(un.charAt(0)));
            this.isUnique = true;
            this.isNonUnique = false;
            this.isAllowed = false;
            hasSetUN = true;
        }
        if (!hasSetUN) {
            this.parseForbiddenFromJPI();
        }
    }

    public void setDecay(Decay d) {
        this.decay = d;
    }

    public Decay getDecay() {
        return this.decay;
    }

    public SDS2XDX findFeedingfromLogft(String logftS, String dlogftS) {
        SDS2XDX BR;
        block7: {
            try {
                double f = this.fTotal().x;
                double dfl = this.fTotal().dxl();
                double dfu = this.fTotal().dxu();
                SDS2XDX F = new SDS2XDX();
                F.setErrorLimit(99);
                F.setValues(f, dfu, dfl);
                SDS2XDX LOGFT = new SDS2XDX(logftS, dlogftS);
                double logft = LOGFT.x();
                double dlogftL = LOGFT.dxl();
                double dlogftU = LOGFT.dxu();
                if (dlogftL < 0.0) {
                    dlogftL = 0.0;
                }
                if (dlogftU < 0.0) {
                    dlogftU = 0.0;
                }
                Level pLev = this.parent.level();
                SDS2XDX T12 = new SDS2XDX(pLev.T12S(), pLev.DT12S());
                String tunit = pLev.T12Unit();
                T12.setErrorLimit(99);
                BR = T12.multiply(F);
                BR = BR.multiply(EnsdfUtil.T12UnitMultiplier(tunit));
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
                if (BR != null && !(BR.x() <= 0.0)) break block7;
                return null;
            }
            catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        return BR;
    }

    private double[] calculatefEC(int nUnique, double W0p) {
        double[] out = new double[4];
        Arrays.fill(out, 0.0);
        if (nUnique < 0 || nUnique > 4) {
            nUnique = 0;
        }
        int n = nUnique;
        double bk = RADConfig.BK[this.Z - 1] / 510.99906;
        double bl1 = RADConfig.BL1[this.Z - 1] / 510.99906;
        double bl2 = RADConfig.BL2[this.Z - 1] / 510.99906;
        double bl3 = RADConfig.BL3[this.Z - 1] / 510.99906;
        double WM1L = RADConfig.WM1L[this.Z - 1];
        double WM3L = RADConfig.WM3L[this.Z - 1];
        int ZP = this.Z + 1;
        WaveFunctionEntry entry = RADConfig.getWFunc(ZP);
        if (entry == null) {
            entry = new WaveFunctionEntry();
        }
        double gKSQ = entry.gKSQ;
        double gL1SQ = entry.gL1SQ;
        double fL2SQ = entry.fL2SQ;
        double gL3SQ_9R2 = entry.gL3SQ_9R2;
        double gfMNO_SQ = entry.gfMNO_SQ;
        double C = 1.5707963267948966;
        double fK = 0.0;
        double fL = 0.0;
        double fL1 = 0.0;
        double fL2 = 0.0;
        double fL3 = 0.0;
        double fMNO = 0.0;
        double fTotal = 0.0;
        double[] A = new double[]{0.0, 1.0, 3.3333333333333335, 7.0, 12.0};
        if (!(W0p > 1.0 - WM1L)) {
            this.message = String.valueOf(this.message) + "Warning: energy is too low for this program\n";
            this.message = String.valueOf(this.message) + "         no calculations can be done\n";
            return out;
        }
        fMNO = C * Math.pow(W0p - 1.0 + WM1L, 2 * n + 2) * gfMNO_SQ;
        if (W0p > bk) {
            fK = C * Math.pow(W0p - bk, 2 * n + 2) * gKSQ;
        } else {
            this.message = String.valueOf(this.message) + "Warning: energy is too low for K-capture\n";
        }
        if (W0p > bl1) {
            fL1 = C * Math.pow(W0p - bl1, 2 * n + 2) * gL1SQ;
        } else {
            this.message = String.valueOf(this.message) + "Warning: energy is too low for L1-capture\n";
        }
        if (W0p > bl2) {
            fL2 = C * Math.pow(W0p - bl2, 2 * n + 2) * fL2SQ;
        } else {
            this.message = String.valueOf(this.message) + "Warning: energy is too low for L2-capture\n";
        }
        if (n >= 1) {
            double fN3;
            double fM3;
            if (W0p > bl3) {
                fL3 = C * A[n] * Math.pow(W0p - bl3, 2 * n) * gL3SQ_9R2;
            } else if (W0p <= bl3) {
                this.message = String.valueOf(this.message) + "Warning: energy is too low for L2-capture\n";
            }
            if (this.Z >= 18 && (fM3 = C * A[n] * Math.pow(W0p - 1.0 + WM3L, 2 * n) * gL3SQ_9R2 * (0.043 + (double)this.Z * (0.004198 - (double)this.Z * 1.6E-5))) > 0.0) {
                fMNO += fM3;
            }
            if (this.Z >= 36 && (fN3 = C * A[n] * Math.pow(W0p, 2 * n) * gL3SQ_9R2 * (-0.00527 + (double)this.Z * (0.002352 - (double)this.Z * 9.85E-6))) > 0.0) {
                fMNO += fN3;
            }
        }
        fL = fL1 + fL2 + fL3;
        out[0] = fTotal = fK + fL + fMNO;
        out[1] = fK;
        out[2] = fL;
        out[3] = fMNO;
        return out;
    }

    public static XDX reparseAI(double ai, double dai, String DS) {
        if (ai == 100.0 && dai == 0.0 && DS.isEmpty()) {
            return new XDX(ai, dai, dai);
        }
        return UncertaintyParser.reparseXDX(ai, dai, DS);
    }

    private int findUniqueFromJPI_old() {
        try {
            Vector<JPI> JPIsV = JPI.JPICoupling(this.parent.level().JPiS(), this.decay.getJFS());
            if (JPIsV.size() > 0) {
                JPI JPI0 = JPIsV.get(0);
                int spin = (int)JPI0.spinValue();
                int parityCode = JPI0.parityCode();
                int n = spin - 1;
                if (n >= 1 && Math.pow(-1.0, n) == (double)parityCode) {
                    return n;
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return 0;
    }

    private void parseForbiddenFromJPI() {
        try {
            this.nForbidden = 0;
            this.nForbiddenUnique = 0;
            this.isUnique = false;
            this.isNonUnique = false;
            this.isAllowed = false;
            int DJ = -1;
            JPI ji = new JPI(this.parent.level().JPiS());
            JPI jf = new JPI(this.decay.getJFS());
            Vector<JPI> JPIsV = JPI.JPICoupling(this.parent.level().JPiS(), this.decay.getJFS());
            if (!ji.isUniqueSpin() || !jf.isUniqueSpin()) {
                return;
            }
            JPI deltaJPI = JPIsV.get(0);
            this.nForbiddenUnique = this.nForbidden = (DJ = (int)deltaJPI.spinValue());
            int parityCode = deltaJPI.parityCode();
            if (parityCode != 0) {
                if (DJ >= 1) {
                    if (Math.pow(-1.0, DJ - 1) == (double)parityCode) {
                        this.nForbidden = DJ - 1;
                        this.nForbiddenUnique = DJ - 1;
                        if (DJ > 1) {
                            this.isUnique = true;
                        } else {
                            this.isAllowed = true;
                        }
                    } else if (Math.pow(-1.0, DJ) == (double)parityCode) {
                        this.nForbiddenUnique = DJ - 1;
                        this.isNonUnique = true;
                    }
                } else if (parityCode == -1) {
                    this.nForbidden = 1;
                    this.nForbiddenUnique = 0;
                    this.isNonUnique = true;
                } else {
                    this.isAllowed = true;
                }
            } else {
                if (DJ == 0) {
                    DJ = 1;
                }
                this.nForbidden = DJ;
                this.nForbiddenUnique = DJ - 1;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public DecaySpectrum makeSpectrum(int nforbidden, int nunique) {
        DecaySpectrum spec = null;
        try {
            spec = new DecaySpectrum(this.Z, this.A, this.W0keV, this.decayType, this.parent, this.decay);
            spec.nForbiddenUnique = nunique;
            spec.nForbidden = nforbidden;
            spec.setIsUnique(nunique > 0);
            spec.calculateLogft();
            this.alternativeSpecsV.add(spec);
        }
        catch (Exception exception) {
            // empty catch block
        }
        return spec;
    }

    private void calculateLogft() {
        SDS2XDX sx;
        Decay d;
        if (this.decay == null || this.W0 <= 1.0 && !this.isECBP) {
            return;
        }
        if (this.isECBP && ((ECBP)this.decay).EPEF_EC() <= 0.0f) {
            return;
        }
        if (this.totalArea.x <= 0.0 && this.W0 > 1.0) {
            this.calculateBetaTotal();
        }
        this.AIB = XDX.ZERO(-1.0);
        this.AIE = XDX.ZERO(-1.0);
        this.AIT = XDX.ZERO(-1.0);
        this.decayBR = XDX.ZERO(-1.0);
        this.decayIT = XDX.ZERO(-1.0);
        this.hasIB = false;
        this.hasIE = false;
        this.hasIT = false;
        if (this.isECBP) {
            d = (ECBP)this.decay;
            if (((ECBP)d).DAIBD() <= 0.0 && ((ECBP)d).DIBS().contains("+")) {
                sx = new SDS2XDX(((ECBP)d).AIBS(), ((ECBP)d).DAIBS());
                this.AIB = new XDX(sx.x(), sx.dxu(), sx.dxl());
            } else {
                this.AIB = DecaySpectrum.reparseAI(((ECBP)d).AIBD(), ((ECBP)d).DAIBD(), ((ECBP)d).DIBS());
            }
            if (((ECBP)d).DAIED() <= 0.0 && ((ECBP)d).DIES().contains("+")) {
                sx = new SDS2XDX(((ECBP)d).AIES(), ((ECBP)d).DAIES());
                this.AIE = new XDX(sx.x(), sx.dxu(), sx.dxl());
            } else {
                this.AIE = DecaySpectrum.reparseAI(((ECBP)d).AIED(), ((ECBP)d).DAIED(), ((ECBP)d).DIES());
            }
            if (((ECBP)d).ITS().isEmpty()) {
                this.hasIT = false;
                XDX IB = null;
                XDX IE = null;
                IB = ((ECBP)d).DIBD() < 0.0 ? new XDX(((ECBP)d).IBS(), ((ECBP)d).DIBS()) : new XDX(((ECBP)d).IBD(), ((ECBP)d).DIBD());
                IE = ((ECBP)d).DIED() < 0.0 ? new XDX(((ECBP)d).IES(), ((ECBP)d).DIES()) : new XDX(((ECBP)d).IED(), ((ECBP)d).DIED());
                if (IB.x > 0.0 && IE.x > 0.0) {
                    this.decayIT = IB.add(IE);
                    this.hasIB = true;
                    this.hasIE = true;
                } else if (IB.x > 0.0) {
                    this.decayIT = IB;
                    this.hasIB = true;
                } else if (IE.x > 0.0) {
                    this.decayIT = IE;
                    this.hasIE = true;
                }
                String DS = "";
                DS = !Str.isNumeric(((ECBP)d).DIBS()) && !((ECBP)d).DIBS().isEmpty() ? ((ECBP)d).DIBS() : (!Str.isNumeric(((ECBP)d).DIES()) && !((ECBP)d).DIES().isEmpty() ? ((ECBP)d).DIES() : ((ECBP)d).DAITS());
                if (((ECBP)d).DAITD() <= 0.0 && DS.contains("+")) {
                    SDS2XDX sx2 = new SDS2XDX(((ECBP)d).AITS(), ((ECBP)d).DAITS());
                    this.AIT = new XDX(sx2.x(), sx2.dxu(), sx2.dxl());
                } else {
                    this.AIT = DecaySpectrum.reparseAI(((ECBP)d).AITD(), ((ECBP)d).DAITD(), DS);
                }
                if (!Str.isNumeric(DS) && !DS.contains("+")) {
                    this.decayIT.dsl = DS;
                    this.decayIT.dsu = DS;
                }
            } else {
                this.hasIT = true;
                if (((ECBP)d).DAITD() <= 0.0 && ((ECBP)d).DITS().contains("+")) {
                    sx = new SDS2XDX(((ECBP)d).AITS(), ((ECBP)d).DAITS());
                    this.AIT = new XDX(sx.x(), sx.dxu(), sx.dxl());
                } else {
                    this.AIT = DecaySpectrum.reparseAI(((ECBP)d).AITD(), ((ECBP)d).DAITD(), ((ECBP)d).DITS());
                }
                this.decayIT = new XDX(((ECBP)d).ITD(), ((ECBP)d).DITD());
                if (!Str.isNumeric(((ECBP)d).DITS())) {
                    if (!((ECBP)d).DITS().contains("+")) {
                        this.decayIT.dsl = ((ECBP)d).DITS();
                        this.decayIT.dsu = ((ECBP)d).DITS();
                    } else {
                        sx = new SDS2XDX(((ECBP)d).ITS(), ((ECBP)d).DITS());
                        this.decayIT = new XDX(sx.x(), sx.dxu(), sx.dxl());
                    }
                }
            }
            this.decayBR = this.AIT.divided(100.0);
        } else {
            d = (Beta)this.decay;
            if (((Beta)d).RIS().length() > 0) {
                this.hasIB = true;
            }
            if (((Beta)d).DAID() <= 0.0 && ((Beta)d).DRIS().contains("+")) {
                sx = new SDS2XDX(((Beta)d).AIS(), ((Beta)d).DAIS());
                this.AIB = new XDX(sx.x(), sx.dxu(), sx.dxl());
            } else {
                this.AIB = DecaySpectrum.reparseAI(((Beta)d).AID(), ((Beta)d).DAID(), ((Beta)d).DRIS());
            }
            this.decayIT = new XDX(((Beta)d).ID(), ((Beta)d).DID());
            if (!Str.isNumeric(((Beta)d).DRIS()) && !((Beta)d).DRIS().contains("+")) {
                this.AIB.dsu = this.AIB.dsl = ((Beta)d).DRIS();
                this.decayIT.dsl = ((Beta)d).DRIS();
                this.decayIT.dsu = ((Beta)d).DRIS();
            }
            this.decayBR = this.AIB.divided(100.0);
        }
        Level pLev = this.parent.level();
        double t = pLev.T12D();
        double dtu = pLev.T12UpperD() - t;
        double dtl = t - pLev.T12LowerD();
        XDX t12 = null;
        if (pLev.DT12S().isEmpty()) {
            t12 = new XDX(t, -1.0);
        } else if (!Str.isNumeric(pLev.DT12S()) && !pLev.DT12S().contains("+")) {
            t12 = new XDX(t, -1.0);
            t12.dsl = pLev.DT12S();
            t12.dsu = pLev.DT12S();
        } else {
            t12 = new XDX(t, dtu, dtl);
        }
        if (this.W0 > 1.0) {
            this.fB.x = (double)Util.fact(2 * this.nForbiddenUnique + 1) * this.totalArea.x;
        }
        this.fTotal = this.fB.add(XDX.ZERO());
        if (this.isRecursive && this.isUnique) {
            this.spec0 = new DecaySpectrum(this.Z, this.A, this.W0keV, this.decayType, this.parent, this.decay);
            this.spec0.nForbiddenUnique = 0;
            this.spec0.setIsUnique(false);
            this.spec0.calculateLogft();
        }
        if (this.isECBP) {
            ECBP d2 = (ECBP)this.decay;
            double r = (double)d2.EF() / ((double)d2.EPEF_EC() - 1021.99812);
            double W0p = this.W0 + 1.0;
            if (r > 0.0) {
                W0p = (this.W0 - 1.0) / r + 2.0;
            }
            double[] fs = this.calculatefEC(this.nForbiddenUnique, W0p);
            this.fEC.x = fs[0];
            this.fK.x = fs[1];
            this.fL.x = fs[2];
            this.fMNO.x = fs[3];
            this.fTotal.x = this.fB.x + this.fEC.x;
            if (this.fEC.x > 0.0 && this.fB.x > 0.0) {
                this.rEC2BP.x = this.fEC.x / this.fB.x;
                this.rEC2BP.dxu = 0.01 * this.rEC2BP.x;
                this.rEC2BP.dxl = 0.01 * this.rEC2BP.x;
            }
            if (this.fTotal.x > 0.0) {
                if (this.fK.x > 0.0) {
                    this.rK2ECBP.x = this.fK.x / this.fTotal.x;
                    this.rK2ECBP.dxu = 0.01 * this.rK2ECBP.x;
                    this.rK2ECBP.dxl = 0.01 * this.rK2ECBP.x;
                }
                if (this.fL.x > 0.0) {
                    this.rL2ECBP.x = this.fL.x / this.fTotal.x;
                    this.rL2ECBP.dxu = 0.01 * this.rL2ECBP.x;
                    this.rL2ECBP.dxl = 0.01 * this.rL2ECBP.x;
                }
                if (this.fMNO.x > 0.0) {
                    this.rMNO2ECBP.x = this.fMNO.x / this.fTotal.x;
                    this.rMNO2ECBP.dxu = 0.01 * this.rMNO2ECBP.x;
                    this.rMNO2ECBP.dxl = 0.01 * this.rMNO2ECBP.x;
                }
            }
            if (this.decayIT.x > 0.0) {
                if (this.rEC2BP.x > 0.0) {
                    XDX temp = XDX.ONE().add(this.rEC2BP);
                    boolean doneCalc = false;
                    if (!(this.hasIT || this.hasIB && this.hasIE)) {
                        if (this.hasIB) {
                            this.calcIBeta = this.decayIT.copy();
                            this.calcIEC = this.calcIBeta.multiply(this.rEC2BP);
                            this.decayIT = this.calcIBeta.multiply(temp);
                            this.decayBR = this.AIB.multiply(temp).divided(100.0);
                            doneCalc = true;
                        } else if (this.hasIE) {
                            this.decayBR = this.AIE.divided(100.0);
                        }
                    }
                    if (!doneCalc && this.decayIT.x > 0.0) {
                        temp = XDX.ONE().divided(temp);
                        this.calcIBeta = this.decayIT.multiply(temp);
                        temp = XDX.ONE().subtract(temp);
                        this.calcIEC = this.decayIT.multiply(temp);
                    }
                    if (this.decayIT.dxu == 0.0) {
                        this.calcIBeta.dxu = 0.0;
                        this.calcIBeta.dxl = 0.0;
                        this.calcIBeta.dsu = "0";
                        this.calcIBeta.dsl = "0";
                        this.calcIEC.dxu = 0.0;
                        this.calcIEC.dxl = 0.0;
                        this.calcIEC.dsu = "0";
                        this.calcIEC.dsl = "0";
                    } else if (this.decayIT.dxu < 0.0) {
                        this.calcIBeta.dxu = -1.0;
                        this.calcIBeta.dxl = -1.0;
                        this.calcIBeta.dsu = this.decayIT.dsu;
                        this.calcIBeta.dsl = this.decayIT.dsl;
                        this.calcIEC.dxu = -1.0;
                        this.calcIEC.dxl = -1.0;
                        this.calcIEC.dsu = this.decayIT.dsu;
                        this.calcIEC.dsl = this.decayIT.dsl;
                    }
                } else if (this.fEC.x > 0.0) {
                    this.calcIEC = this.decayIT.multiply(1.0, 0.0);
                    if (this.decayIT.dxu == 0.0) {
                        this.calcIEC.dxu = 0.0;
                        this.calcIEC.dxl = 0.0;
                        this.calcIEC.dsu = "0";
                        this.calcIEC.dsl = "0";
                    } else if (this.decayIT.dxu < 0.0) {
                        this.calcIEC.dxu = -1.0;
                        this.calcIEC.dxl = -1.0;
                        this.calcIEC.dsu = this.decayIT.dsu;
                        this.calcIEC.dsl = this.decayIT.dsl;
                    }
                }
            }
        }
        if (this.decayBR.x > 0.0) {
            this.pT12 = t12.divided(this.decayBR);
        }
        if (this.fTotal.x > 0.0) {
            this.logf.x = Math.log10(this.fTotal.x);
        }
        this.ft.x = this.fTotal.x * this.pT12.x;
        if (this.ft.x > 0.0) {
            this.logft.x = Math.log10(this.ft.x);
        }
        if (this.pT12.dxu > 0.0) {
            this.ft.dxu = this.fTotal.x * this.pT12.dxu;
            if (this.ft.x + this.ft.dxu > 0.0) {
                this.logft.dxu = Math.log10(this.ft.x + this.ft.dxu) - this.logft.x;
            }
        }
        if (this.pT12.dxl > 0.0) {
            this.ft.dxl = this.fTotal.x * this.pT12.dxl;
            if (this.ft.x - this.ft.dxl > 0.0) {
                this.logft.dxl = this.logft.x - Math.log10(this.ft.x - this.ft.dxl);
                if (this.logft.dxl < 0.0 && this.logft.dxu >= 0.0) {
                    this.logft.dxl = 0.0;
                }
            } else {
                this.logft.x = Math.log10(this.ft.x + this.ft.dxu);
                this.logft.dsu = "LE";
                this.logft.dsl = "LE";
                this.logft.dxu = -1.0;
                this.logft.dxl = -1.0;
            }
        }
        this.setSDS(this.fEC);
        this.setSDS(this.fK);
        this.setSDS(this.fL);
        this.setSDS(this.fMNO);
        this.setSDS(this.fB);
        this.setSDS(this.fTotal);
        this.setSDS(this.logf);
        this.setSDS(this.ft);
        this.setSDS(this.logft);
        this.setSDS(this.pT12);
        if (!Str.isNumeric(this.pT12.dsl) && !this.pT12.dsl.isEmpty()) {
            this.ft.dsl = this.pT12.dsl;
            this.ft.dsu = this.pT12.dsl;
            this.logft.dsl = this.pT12.dsl;
            this.logft.dsu = this.pT12.dsl;
        }
    }

    private void calculateBetaTotal() {
        if (this.W0 <= 1.0) {
            return;
        }
        this.Fermi = W -> this.getFermiFunc0(W, this.precisionFermiBeta);
        this.totalArea.x = this.romb(this.Fermi, 1.0, this.W0, this.precisionRombBeta);
        this.wFermi = W -> W * this.getFermiFunc0(W, this.precisionFermiBeta);
        if (this.totalArea.x > 0.0) {
            this.averageTotalEnergy.x = this.romb(this.wFermi, 1.0, this.W0, this.precisionRombBeta) / this.totalArea.x;
        }
    }

    private void calculateBetaInterval() {
        if (this.E.length == 0 || this.W0 <= 1.0) {
            return;
        }
        this.area = new XDX[this.E.length];
        this.Earea = new XDX[this.E.length];
        this.EareaInkeV = new XDX[this.E.length];
        int i = 0;
        while (i < this.E.length) {
            this.area[i] = XDX.ZERO();
            this.Earea[i] = XDX.ZERO();
            this.EareaInkeV[i] = XDX.ZERO();
            ++i;
        }
        i = 0;
        while (i < this.E.length - 1) {
            double W1 = this.E[i];
            if (!(W1 >= this.W0)) {
                double W2 = this.E[i + 1];
                this.area[i].x = this.romb(this.Fermi, W1, W2, this.precisionRombBeta);
                this.Earea[i].x = this.romb(this.wFermi, W1, W2, this.precisionRombBeta);
                this.EareaInkeV[i].x = (this.Earea[i].x - this.area[i].x) * 510.99906 / this.totalArea.x;
                this.area[i].x /= this.totalArea.x;
                this.Earea[i].x /= this.totalArea.x;
            }
            ++i;
        }
    }

    private void calculateIB() {
        this.calculateIBFromBeta();
        if (this.isECBP && this.calculateIBFromEC) {
            this.calculateIBFromEC();
        }
    }

    private void calculateIBFromBeta() {
        if (this.EIB.length <= 1 || this.W0 <= 1.0) {
            return;
        }
        this.areaIBbeta = new XDX[this.EIB.length];
        this.EareaIBbeta = new XDX[this.EIB.length];
        this.EareaIBbetaInkeV = new XDX[this.EIB.length];
        int i = 0;
        while (i < this.EIB.length) {
            this.areaIBbeta[i] = XDX.ZERO();
            this.EareaIBbeta[i] = XDX.ZERO();
            this.EareaIBbetaInkeV[i] = XDX.ZERO();
            ++i;
        }
        Function1D SF = e -> this.getIBStrenFunc0(e);
        Function1D eSF = e -> e * this.getIBStrenFunc0(e);
        double totalAreaIB = 0.0;
        double maxE = this.W0 - 1.0;
        int i2 = 0;
        while (i2 < this.EIB.length - 1) {
            double e1 = this.EIB[i2];
            if (i2 != this.E.length - 1 && !(e1 >= maxE)) {
                double e2 = this.EIB[i2 + 1];
                if (e2 > maxE) {
                    e2 = maxE * 0.999999999;
                }
                this.areaIBbeta[i2].x = e1 > 0.0 ? this.romb(SF, e1, e2, this.precisionRombIB) * 0.0023229412952889108 : 0.0;
                this.EareaIBbeta[i2].x = this.romb(eSF, e1, e2, this.precisionRombIB) * 0.0023229412952889108;
                this.EareaIBbetaInkeV[i2].x = this.EareaIBbeta[i2].x * 510.99906;
                if (this.areaIBbeta[i2].x > 0.0) {
                    totalAreaIB += this.areaIBbeta[i2].x;
                    if (e1 > 0.05) {
                        this.areaIBbeta[i2] = this.areaIBbeta[i2].multiply(1.0, 0.1);
                    }
                    if (this.nForbiddenUnique > 0) {
                        this.areaIBbeta[i2] = this.areaIBbeta[i2].multiply(1.0, 0.05);
                    }
                }
            }
            ++i2;
        }
        if (totalAreaIB > 0.0) {
            totalAreaIB = this.totalArea.x;
            i2 = 0;
            while (i2 < this.EIB.length - 1) {
                this.areaIBbeta[i2].x /= totalAreaIB;
                this.EareaIBbeta[i2].x /= totalAreaIB;
                this.EareaIBbetaInkeV[i2].x /= totalAreaIB;
                ++i2;
            }
        }
    }

    private void calculateIBFromEC() {
        boolean is1SOnly;
        double[] ZSC = new double[]{10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0};
        double[] S1S = new double[]{0.87, 0.91, 0.93, 0.95, 0.97, 0.98, 0.98, 0.98, 0.99, 0.99};
        double[] S2S = new double[]{0.51, 0.62, 0.69, 0.74, 0.79, 0.82, 0.85, 0.87, 0.88, 0.88};
        double[] S2P = new double[]{0.35, 0.48, 0.55, 0.6, 0.65, 0.7, 0.72, 0.75, 0.78, 0.79};
        double[] S3P = new double[]{0.0, 0.1, 0.23, 0.32, 0.4, 0.48, 0.53, 0.58, 0.61, 0.63};
        if (this.EIB.length <= 1 || this.decay == null || !this.isECBP) {
            return;
        }
        this.areaIBec = new XDX[this.EIB.length];
        this.EareaIBec = new XDX[this.EIB.length];
        this.EareaIBecInkeV = new XDX[this.EIB.length];
        int i = 0;
        while (i < this.EIB.length) {
            this.areaIBec[i] = XDX.ZERO();
            this.EareaIBec[i] = XDX.ZERO();
            this.EareaIBecInkeV[i] = XDX.ZERO();
            ++i;
        }
        ECBP d = (ECBP)this.decay;
        double E0 = (double)d.EPEF_EC() / 510.99906;
        double kf = d.CKF();
        double dkf = d.DCKF();
        if (dkf < 0.0) {
            dkf = 0.0;
        }
        if (E0 <= 0.0 || kf <= 0.0) {
            return;
        }
        double bk = RADConfig.BK[this.Z - 1] / 510.99906;
        double bl1 = RADConfig.BL1[this.Z - 1] / 510.99906;
        double bl2 = RADConfig.BL2[this.Z - 1] / 510.99906;
        double bl3 = RADConfig.BL3[this.Z - 1] / 510.99906;
        if (bk == 0.0) {
            this.message = String.valueOf(this.message) + this.indent + "Error: can't calculate any IB from EC\n";
            return;
        }
        boolean bl = is1SOnly = bl1 == 0.0 || this.Z < 10;
        if (is1SOnly) {
            this.message = String.valueOf(this.message) + this.indent + "Only IB fom 1S shell is calculated\n";
        }
        this.maxE1S = E0 - bk;
        this.maxE2S = E0 - bl1;
        this.maxE2P = E0 - bl2;
        this.maxE3P = E0 - bl3;
        this.beK = bk;
        this.r_gL1gK_SQ = this.Z > 98 ? RADConfig.R_gL1gK_SQ[97] : RADConfig.R_gL1gK_SQ[this.Z - 1];
        Vector<DataPoint2D> dpsV = new Vector<DataPoint2D>();
        dpsV.clear();
        int i2 = 0;
        while (i2 < ZSC.length) {
            DataPoint2D dp = new DataPoint2D(ZSC[i2], S2S[i2] / S1S[i2]);
            dpsV.add(dp);
            ++i2;
        }
        double[] ydy = Util.polynomialInterpolation(this.Z, dpsV);
        double screen2S = ydy[0];
        double dscreen2S = ydy[1];
        dpsV.clear();
        int i3 = 0;
        while (i3 < ZSC.length) {
            DataPoint2D dp = new DataPoint2D(ZSC[i3], S2P[i3] / S1S[i3]);
            dpsV.add(dp);
            ++i3;
        }
        ydy = Util.polynomialInterpolation(this.Z, dpsV);
        double screen2P = ydy[0];
        double dscreen2P = ydy[1];
        dpsV.clear();
        int i4 = 0;
        while (i4 < ZSC.length) {
            DataPoint2D dp = new DataPoint2D(ZSC[i4], S3P[i4] / S1S[i4]);
            dpsV.add(dp);
            ++i4;
        }
        ydy = Util.polynomialInterpolation(this.Z, dpsV);
        double screen3P = ydy[0];
        double dscreen3P = ydy[1];
        Function1D fIB1S = e -> this.probIB_1S(e);
        Function1D feIB1S = e -> e * this.probIB_1S(e);
        Integrate int1S = (f, e1, e2) -> {
            if (e1 < this.maxE1S) {
                return this.romb(f, e1, e2, this.precisionIBfromEC);
            }
            return 0.0;
        };
        Function1D fIB2S = e -> this.probIB_2S(e);
        Function1D feIB2S = e -> e * this.probIB_2S(e);
        Integrate int2S = (f, e1, e2) -> {
            if (screen2S > 1.0E-9 && e1 < this.maxE2S) {
                return screen2S * this.romb(f, e1, e2, this.precisionIBfromEC);
            }
            return 0.0;
        };
        Function1D fIB2P = e -> this.probIB_2P(e);
        Function1D feIB2P = e -> e * this.probIB_2P(e);
        Integrate int2P = (f, e1, e2) -> {
            if (screen2P > 1.0E-9 && e1 < this.maxE2P) {
                return screen2P * this.romb(f, e1, e2, this.precisionIBfromEC);
            }
            return 0.0;
        };
        Function1D fIB3P = e -> this.probIB_3P(e);
        Function1D feIB3P = e -> e * this.probIB_3P(e);
        Integrate int3P = (f, e1, e2) -> {
            if (screen3P > 1.0E-9 && e1 < this.maxE3P) {
                return screen3P * this.romb(f, e1, e2, this.precisionIBfromEC);
            }
            return 0.0;
        };
        double area = 0.0;
        double Earea = 0.0;
        int i5 = 0;
        while (i5 < this.EIB.length - 1) {
            double e12 = this.EIB[i5];
            if (e12 != 0.0) {
                if (e12 >= E0) break;
                double e22 = Math.min(this.EIB[i5 + 1], E0);
                area = int1S.doIntegrate(fIB1S, e12, e22);
                Earea = int1S.doIntegrate(feIB1S, e12, e22);
                this.areaIBec[i5].x += area;
                this.EareaIBec[i5].x += Earea;
                double dscreen = 0.0;
                dscreen = e22 <= bk ? 0.2 : (e22 <= 2.0 * bk ? 0.1 : 0.02);
                double da = 0.0;
                double dea = 0.0;
                da += Math.pow(dscreen * area, 2.0);
                dea += Math.pow(dscreen * Earea, 2.0);
                if (is1SOnly) {
                    this.areaIBec[i5].dxl = this.areaIBec[i5].dxu = Math.sqrt(da);
                    this.EareaIBec[i5].dxl = this.EareaIBec[i5].dxu = Math.sqrt(dea);
                } else {
                    double r;
                    area = int2S.doIntegrate(fIB2S, e12, e22);
                    Earea = int2S.doIntegrate(feIB2S, e12, e22);
                    this.areaIBec[i5].x += area;
                    this.EareaIBec[i5].x += Earea;
                    if (area > 0.0) {
                        r = 0.0;
                        r = 0.02 / (screen2S * screen2S);
                        r += Math.pow(dscreen2S / screen2S, 2.0);
                        da += (r += dscreen * dscreen) * area * area;
                        dea += r * Earea * Earea;
                    }
                    area = int2P.doIntegrate(fIB2P, e12, e22);
                    Earea = int2P.doIntegrate(feIB2P, e12, e22);
                    this.areaIBec[i5].x += area;
                    this.EareaIBec[i5].x += Earea;
                    if (area > 0.0) {
                        r = 0.0;
                        r = 0.01 / (screen2P * screen2P);
                        r += Math.pow(dscreen2P / screen2P, 2.0);
                        da += (r += dscreen * dscreen) * area * area;
                        dea += r * Earea * Earea;
                    }
                    area = int3P.doIntegrate(fIB3P, e12, e22);
                    Earea = int3P.doIntegrate(feIB3P, e12, e22);
                    this.areaIBec[i5].x += area;
                    this.EareaIBec[i5].x += Earea;
                    if (area > 0.0) {
                        r = 0.0;
                        r = 0.01 / (screen3P * screen3P);
                        r += Math.pow(dscreen3P / screen3P, 2.0);
                        da += (r += dscreen * dscreen) * area * area;
                        dea += r * Earea * Earea;
                    }
                    this.areaIBec[i5].dxl = this.areaIBec[i5].dxu = Math.sqrt(da);
                    this.EareaIBec[i5].dxl = this.EareaIBec[i5].dxu = Math.sqrt(dea);
                }
            }
            ++i5;
        }
        i5 = 0;
        while (i5 < this.EIB.length - 1) {
            this.areaIBec[i5] = this.areaIBec[i5].multiply(kf, dkf);
            this.EareaIBec[i5] = this.EareaIBec[i5].multiply(kf, dkf);
            this.EareaIBecInkeV[i5] = this.EareaIBec[i5].multiply(510.99906);
            ++i5;
        }
    }

    private double KFunc(double lambda, double eta) {
        double v = 0.0;
        if (lambda < 1.0E-10) {
            return 0.0;
        }
        double v0 = Math.log(1.0 + lambda);
        int j = 1;
        while (!(Math.abs((v = v0 - eta * Math.pow(-lambda, j) / ((double)j * ((double)j - eta))) - v0) <= Math.abs(v0) * 1.0E-6)) {
            v0 = v;
            if (++j <= 20) continue;
        }
        return v;
    }

    private double probIB_1S(double e) {
        if (e <= 0.0 || e >= this.maxE1S) {
            return 0.0;
        }
        double C = 0.0023229412952889108;
        double v = 0.0;
        try {
            v = C * e * Math.pow(1.0 - e / this.maxE1S, 2.0) * this.R1S(e);
            if (this.nForbiddenUnique == 1) {
                v *= Math.pow(1.0 - e / this.maxE1S, 2.0) + Math.pow(e / this.maxE1S, 2.0);
            }
            return v;
        }
        catch (Exception exception) {
            return v;
        }
    }

    private double R1S(double e) {
        double v = 0.0;
        Vector<DataPoint2D> pointsV = RADConfig.eR1SpointsV;
        int N = pointsV.size();
        int nStepMax = 6;
        if ((e /= this.beK) >= pointsV.get((int)0).x && e <= pointsV.get((int)(N - 1)).x) {
            double[] yDy = Util.polynomialInterpolation(e, pointsV, nStepMax);
            double y = yDy[0];
            double dy = yDy[1];
            if (dy >= 0.0 && dy <= y * 1.0E-4) {
                return y;
            }
        }
        try {
            double eta1 = 1.0 / Math.sqrt(1.0 + e);
            double lambda1 = (1.0 - eta1) / (1.0 + eta1);
            double r1s = 1.0 - 1.3333333333333333 * eta1 / (1.0 - eta1) * (1.0 + eta1 / (1.0 - eta1) * (2.0 * this.KFunc(lambda1, eta1) - 1.0));
            r1s = (1.0 + r1s * r1s) / 2.0;
            return r1s;
        }
        catch (Exception exception) {
            return v;
        }
    }

    private double probIB_2S(double e) {
        if (e <= 0.0 || e >= this.maxE2S) {
            return 0.0;
        }
        double C = 0.0023229412952889108;
        double v = 0.0;
        try {
            v = C * e * Math.pow((this.maxE2S - e) / this.maxE1S, 2.0) * this.R2S(e) * this.r_gL1gK_SQ;
            return v;
        }
        catch (Exception exception) {
            return v;
        }
    }

    private double R2S(double e) {
        double v = 0.0;
        Vector<DataPoint2D> pointsV = RADConfig.eR2SpointsV;
        int N = pointsV.size();
        int nStepMax = 6;
        if ((e /= this.beK) >= pointsV.get((int)0).x && e <= pointsV.get((int)(N - 1)).x) {
            double[] yDy = Util.polynomialInterpolation(e, pointsV, nStepMax);
            double y = yDy[0];
            double dy = yDy[1];
            if (dy >= 0.0 && dy <= y * 1.0E-4) {
                return y;
            }
        }
        try {
            double eta2 = 1.0 / Math.sqrt(0.25 + e);
            double lambda2 = (2.0 - eta2) / (2.0 + eta2);
            double r2s = 1.0 - eta2 / (1.0 - eta2 * eta2 / 4.0) * (1.3333333333333333 + 5.0 * eta2 / 6.0);
            r2s -= eta2 * eta2 / Math.pow(1.0 - eta2 * eta2 / 4.0, 2.0) * (2.6666666666666665 * (1.0 - eta2 * eta2) * this.KFunc(lambda2, eta2) - 3.0 - eta2 + 5.0 * eta2 * eta2 / 4.0);
            r2s = (1.0 + r2s * r2s) / 2.0;
            return r2s;
        }
        catch (Exception exception) {
            return v;
        }
    }

    private double probIB_2P(double e) {
        if (e <= 0.0 || e >= this.maxE2P) {
            return 0.0;
        }
        double C = 4.0 / (Math.PI * (double)this.Z * (double)this.Z * 0.007297735308);
        double v = 0.0;
        try {
            v = C * e * Math.pow((this.maxE2P - e) / this.maxE1S, 2.0) * this.Q2P(e);
            return v;
        }
        catch (Exception exception) {
            return v;
        }
    }

    private double Q2P(double e) {
        double v = 0.0;
        Vector<DataPoint2D> pointsV = RADConfig.eQ2PpointsV;
        int N = pointsV.size();
        int nStepMax = 6;
        if ((e /= this.beK) >= pointsV.get((int)0).x && e <= pointsV.get((int)(N - 1)).x) {
            double[] yDy = Util.polynomialInterpolation(e, pointsV, nStepMax);
            double y = yDy[0];
            double dy = yDy[1];
            if (dy >= 0.0 && dy <= y * 1.0E-4) {
                return y * y;
            }
        }
        try {
            double eta2 = 1.0 / Math.sqrt(0.25 + e);
            double lambda2 = (2.0 - eta2) / (2.0 + eta2);
            double etaSQ = eta2 * eta2;
            double q2p = etaSQ / (4.0 * Math.pow(1.0 - etaSQ / 4.0, 2.0));
            q2p *= 1.0 + 2.0 * eta2 / 3.0 - 7.0 * etaSQ / 12.0 + 1.3333333333333333 * etaSQ * this.KFunc(lambda2, eta2);
            q2p *= q2p;
            return q2p;
        }
        catch (Exception exception) {
            return v;
        }
    }

    private double probIB_3P(double e) {
        if (e <= 0.0 || e >= this.maxE3P) {
            return 0.0;
        }
        double C = 4.0 / (Math.PI * (double)this.Z * (double)this.Z * 0.007297735308);
        double v = 0.0;
        try {
            v = C * e * Math.pow((this.maxE3P - e) / this.maxE1S, 2.0) * this.Q3P(e);
            return v;
        }
        catch (Exception exception) {
            return v;
        }
    }

    private double Q3P(double e) {
        double v = 0.0;
        Vector<DataPoint2D> pointsV = RADConfig.eQ3PpointsV;
        int N = pointsV.size();
        int nStepMax = 6;
        if ((e /= this.beK) >= pointsV.get((int)0).x && e <= pointsV.get((int)(N - 1)).x) {
            double[] yDy = Util.polynomialInterpolation(e, pointsV, nStepMax);
            double y = yDy[0];
            double dy = yDy[1];
            if (dy >= 0.0 && dy <= y * 1.0E-4) {
                return y * y;
            }
        }
        try {
            double eta3 = 1.0 / Math.sqrt(0.1111111111111111 + e);
            double lambda3 = (3.0 - eta3) / (3.0 + eta3);
            double etaSQ = eta3 * eta3;
            double etaCU = Math.pow(eta3, 3.0);
            double q3p = 0.14814814814814814 * etaSQ / Math.pow(1.0 - etaSQ / 9.0, 3.0);
            q3p *= (1.0 - eta3 / 3.0) * (1.0 + eta3 - 2.0 * etaSQ / 9.0 - 8.0 * etaCU / 27.0) + 1.3333333333333333 * etaSQ * (1.0 - etaSQ / 3.0) * this.KFunc(lambda3, eta3);
            q3p *= q3p;
            return q3p;
        }
        catch (Exception exception) {
            return v;
        }
    }

    private void makeXDXsForLogft(DecaySpectrum spec, DecaySpectrum specP, DecaySpectrum specM) {
        try {
            spec.fTotal = this.makeXDX(spec.fTotal, specP.fTotal, specM.fTotal);
            spec.fB = this.makeXDX(spec.fB, specP.fB, specM.fB);
            spec.fEC = this.makeXDX(spec.fEC, specP.fEC, specM.fEC);
            spec.fK = this.makeXDX(spec.fK, specP.fK, specM.fK);
            spec.fL = this.makeXDX(spec.fL, specP.fL, specM.fL);
            spec.fMNO = this.makeXDX(spec.fMNO, specP.fMNO, specM.fMNO);
            spec.logf = this.makeXDX(spec.logf, specP.logf, specM.logf);
            spec.rEC2BP = this.makeXDX(spec.rEC2BP, specP.rEC2BP, specM.rEC2BP);
            spec.rK2ECBP = this.makeXDX(spec.rK2ECBP, specP.rK2ECBP, specM.rK2ECBP);
            spec.rL2ECBP = this.makeXDX(spec.rL2ECBP, specP.rL2ECBP, specM.rL2ECBP);
            spec.rMNO2ECBP = this.makeXDX(spec.rMNO2ECBP, specP.rMNO2ECBP, specM.rMNO2ECBP);
            spec.ft = this.makeXDX(spec.ft, specP.ft, specM.ft);
            spec.logft = this.makeXDX(spec.logft, specP.logft, specM.logft);
            spec.calcIBeta = this.makeXDX(spec.calcIBeta, specP.calcIBeta, specM.calcIBeta);
            spec.calcIEC = this.makeXDX(spec.calcIEC, specP.calcIEC, specM.calcIEC);
            String DQS = this.parent.DQS();
            if (!DQS.isEmpty()) {
                Str.isNumeric(DQS);
            }
            String DTS = this.parent.level().DT12S();
            DTS.isEmpty();
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public void calculate(float[] gridE, float[] gridEIB, boolean calculateIB) {
        String dts;
        this.setGridE(gridE);
        this.setGridEIB(gridEIB);
        double tempRombPrecision = this.precisionRombBeta;
        double tempFermiPrecision = this.precisionFermiBeta;
        this.calculateBetaTotal();
        this.calculateLogft();
        if (this.totalArea.x > 0.0) {
            this.calculateBetaInterval();
        }
        if (calculateIB) {
            this.calculateIB();
        }
        DecaySpectrum tempSpecP = null;
        DecaySpectrum tempSpecM = null;
        if (this.dW0keV > 0.0) {
            double tempW0keV = this.W0keV + this.dW0keV;
            if (this.W0keV < 0.0 && tempW0keV > 0.0) {
                tempW0keV = 0.0;
            }
            tempSpecP = new DecaySpectrum(this.Z, this.A, tempW0keV, this.decayType, this.parent, this.decay);
            tempSpecP.precisionRombBeta = this.precisionRombBeta;
            tempSpecP.precisionFermiBeta = this.precisionFermiBeta;
            tempSpecP.calculateLogft();
            tempSpecM = new DecaySpectrum(this.Z, this.A, this.W0keV - this.dW0keV, this.decayType, this.parent, this.decay);
            tempSpecM.precisionRombBeta = this.precisionRombBeta;
            tempSpecM.precisionFermiBeta = this.precisionFermiBeta;
            tempSpecM.calculateLogft();
            this.makeXDXsForLogft(this, tempSpecP, tempSpecM);
            this.makeXDXsForLogft(this.spec0(), tempSpecP.spec0(), tempSpecM.spec0());
            if (this.totalArea.x > 0.0) {
                int i;
                tempSpecP.calculateIBFromEC = false;
                tempSpecP.setGrid(gridE, gridEIB);
                if (tempSpecP.totalArea.x <= 0.0) {
                    tempSpecP.calculateBetaTotal();
                }
                tempSpecM.calculateIBFromEC = false;
                tempSpecM.setGrid(gridE, gridEIB);
                if (tempSpecM.totalArea.x <= 0.0) {
                    tempSpecM.calculateBetaTotal();
                }
                double x = this.totalArea.x;
                double xp = tempSpecP.totalArea.x;
                double xm = tempSpecM.totalArea.x;
                if (xp > x && xm > x || xp < x && xm < x && xp > 0.0 && xm > 0.0) {
                    this.precisionRombBeta /= 10.0;
                    this.precisionFermiBeta /= 10.0;
                    this.calculateBetaTotal();
                    this.calculateLogft();
                    tempSpecP.precisionRombBeta = this.precisionRombBeta;
                    tempSpecP.precisionFermiBeta = this.precisionFermiBeta;
                    tempSpecP.totalArea.x = 0.0;
                    tempSpecP.calculateLogft();
                    tempSpecM.precisionRombBeta = this.precisionRombBeta;
                    tempSpecM.precisionFermiBeta = this.precisionFermiBeta;
                    tempSpecM.totalArea.x = 0.0;
                    tempSpecM.calculateLogft();
                }
                if (tempSpecP.totalArea.x > 0.0) {
                    this.totalArea.dxu = tempSpecP.totalArea.x - this.totalArea.x;
                    this.averageTotalEnergy.dxu = tempSpecP.averageTotalEnergy.x - this.averageTotalEnergy.x;
                    if (Math.abs(tempSpecP.totalArea.x - this.totalArea.x) > this.totalArea.x * this.precisionRombBeta / 2.0) {
                        tempSpecP.calculateBetaInterval();
                        tempSpecP.hasCalculatedBeta = true;
                        i = 0;
                        while (i < this.area.length) {
                            this.area[i].dxu = tempSpecP.area[i].x - this.area[i].x;
                            this.Earea[i].dxu = tempSpecP.Earea[i].x - this.Earea[i].x;
                            this.EareaInkeV[i].dxu = tempSpecP.EareaInkeV[i].x - this.EareaInkeV[i].x;
                            ++i;
                        }
                        if (calculateIB) {
                            tempSpecP.calculateIB();
                            i = 0;
                            while (i < this.areaIBbeta.length) {
                                this.areaIBbeta[i].dxu = tempSpecP.areaIBbeta[i].x - this.areaIBbeta[i].x;
                                this.EareaIBbeta[i].dxu = tempSpecP.EareaIBbeta[i].x - this.EareaIBbeta[i].x;
                                this.EareaIBbetaInkeV[i].dxu = tempSpecP.EareaIBbetaInkeV[i].x - this.EareaIBbetaInkeV[i].x;
                                ++i;
                            }
                        }
                    } else {
                        i = 0;
                        while (i < this.area.length) {
                            this.area[i].dxu = 0.0;
                            this.Earea[i].dxu = 0.0;
                            this.EareaInkeV[i].dxu = 0.0;
                            ++i;
                        }
                        i = 0;
                        while (i < this.areaIBbeta.length) {
                            this.areaIBbeta[i].dxu = 0.0;
                            this.EareaIBbeta[i].dxu = 0.0;
                            this.EareaIBbetaInkeV[i].dxu = 0.0;
                            ++i;
                        }
                    }
                }
                if (tempSpecM.totalArea.x > 0.0) {
                    this.totalArea.dxl = this.totalArea.x - tempSpecM.totalArea.x;
                    this.averageTotalEnergy.dxl = this.averageTotalEnergy.x - tempSpecM.averageTotalEnergy.x;
                    if (Math.abs(tempSpecM.totalArea.x - this.totalArea.x) > this.totalArea.x * this.precisionRombBeta / 2.0) {
                        tempSpecM.calculateBetaInterval();
                        tempSpecM.hasCalculatedBeta = true;
                        i = 0;
                        while (i < this.area.length) {
                            this.area[i].dxl = tempSpecM.area[i].x - this.area[i].x;
                            this.Earea[i].dxl = tempSpecM.Earea[i].x - this.Earea[i].x;
                            this.EareaInkeV[i].dxl = tempSpecM.EareaInkeV[i].x - this.EareaInkeV[i].x;
                            ++i;
                        }
                        if (calculateIB) {
                            tempSpecM.calculateIB();
                            i = 0;
                            while (i < this.areaIBbeta.length) {
                                this.areaIBbeta[i].dxl = -tempSpecM.areaIBbeta[i].x + this.areaIBbeta[i].x;
                                this.EareaIBbeta[i].dxl = -tempSpecM.EareaIBbeta[i].x + this.EareaIBbeta[i].x;
                                this.EareaIBbetaInkeV[i].dxl = -tempSpecM.EareaIBbetaInkeV[i].x + this.EareaIBbetaInkeV[i].x;
                                ++i;
                            }
                        }
                    } else {
                        i = 0;
                        while (i < this.area.length) {
                            this.area[i].dxl = 0.0;
                            this.Earea[i].dxl = 0.0;
                            this.EareaInkeV[i].dxl = 0.0;
                            ++i;
                        }
                        i = 0;
                        while (i < this.areaIBbeta.length) {
                            this.areaIBbeta[i].dxl = 0.0;
                            this.EareaIBbeta[i].dxl = 0.0;
                            this.EareaIBbetaInkeV[i].dxl = 0.0;
                            ++i;
                        }
                    }
                }
                this.checkNegativeUncertainties(this.area);
                this.checkNegativeUncertainties(this.Earea);
                this.checkNegativeUncertainties(this.EareaInkeV);
                this.checkNegativeUncertainty(this.totalArea);
                this.checkNegativeUncertainty(this.averageTotalEnergy);
                if (calculateIB) {
                    this.checkNegativeUncertainties(this.areaIBbeta);
                    this.checkNegativeUncertainties(this.EareaIBbeta);
                    this.checkNegativeUncertainties(this.EareaIBbetaInkeV);
                }
            }
        }
        String des = "";
        boolean setDES = false;
        boolean hasSetDTS = false;
        if (!Str.isNumeric(this.decay.DES()) && this.decay.DES().length() > 0 && !this.decay.DES().contains("+")) {
            des = this.decay.DES();
            setDES = true;
        } else if (!Str.isNumeric(this.parent.DQS())) {
            des = this.parent.DQS();
            setDES = true;
        } else if (!Str.isNumeric(this.parent.level().DES()) && this.parent.level().EF() != 0.0f) {
            float dq;
            String ds = this.parent.level().DES();
            float ef = this.parent.level().EF();
            if (!(ef <= (dq = this.parent.DQF())) || ds.startsWith("G") || !"CA,SY".contains(ds)) {
                des = this.parent.level().DES();
                setDES = true;
            }
        }
        if (setDES) {
            this.setUncertainties(this.area, des);
            this.setUncertainties(this.Earea, des);
            this.setUncertainties(this.EareaInkeV, des);
            this.setUncertainty(this.totalArea, des);
            this.setUncertainty(this.averageTotalEnergy, des);
            if (calculateIB) {
                this.setUncertainties(this.areaIBbeta, des);
                this.setUncertainties(this.EareaIBbeta, des);
                this.setUncertainties(this.EareaIBbetaInkeV, des);
            }
            if (this.fTotal.x > 0.0) {
                this.setUncertainty(this.fTotal, des);
                this.setUncertainty(this.fB, des);
                this.setUncertainty(this.logf, des);
                this.setUncertainty(this.rEC2BP, des);
                this.setUncertainty(this.rK2ECBP, des);
                this.setUncertainty(this.rL2ECBP, des);
                this.setUncertainty(this.rMNO2ECBP, des);
                if (this.fEC.x > 0.0) {
                    this.setUncertainty(this.fEC, des);
                    this.setUncertainty(this.fK, des);
                    this.setUncertainty(this.fL, des);
                    this.setUncertainty(this.fMNO, des);
                    if (this.decay.ITS().isEmpty() && this.decay.IES().isEmpty()) {
                        this.setUncertainty(this.decayIT, des);
                        this.setUncertainty(this.calcIEC, des);
                    } else {
                        this.setUncertainty(this.calcIEC, des);
                        if (des.startsWith("G")) {
                            des = "L" + des.substring(1);
                        } else if (des.startsWith("L")) {
                            des = "G" + des.substring(1);
                        }
                        this.setUncertainty(this.calcIBeta, des);
                    }
                    this.calcIBeta.dsl.startsWith("G");
                    this.calcIEC.dsl.startsWith("G");
                }
                if (!Str.isNumeric(this.pT12.dsl) && !this.pT12.dsl.isEmpty()) {
                    char c2;
                    char c1;
                    dts = this.pT12.dsl;
                    if (des.isEmpty()) {
                        des = " ";
                    }
                    if (dts.isEmpty()) {
                        dts = " ";
                    }
                    if ((c1 = des.charAt(0)) == (c2 = dts.charAt(0))) {
                        if (!(c1 != 'L' && c1 != 'G' || des.equals(dts))) {
                            des = String.valueOf(c1) + "T";
                        }
                    } else if ("LG".contains(String.valueOf(c1)) && "LG".contains(String.valueOf(c2))) {
                        des = "";
                    } else if ("LG".contains(String.valueOf(c1))) {
                        des = String.valueOf(c1) + "T";
                    } else if ("LG".contains(String.valueOf(c2))) {
                        des = String.valueOf(c2) + "T";
                    } else if (c1 == 'C' || c2 == 'C') {
                        des = "CA";
                    } else if (c1 == 'S' || c2 == 'S') {
                        des = "SY";
                    }
                }
                des = des.trim();
                this.setUncertainty(this.logft, des);
                this.setUncertainty(this.ft, des);
                hasSetDTS = true;
            }
        }
        dts = "";
        if (!hasSetDTS && !Str.isNumeric(this.pT12.dsl)) {
            dts = this.pT12.dsl;
            this.setUncertainty(this.logft, dts);
            this.setUncertainty(this.ft, dts);
        }
        this.precisionRombBeta = tempRombPrecision;
        this.precisionFermiBeta = tempFermiPrecision;
    }

    private void setUncertainties(XDX[] area, String ds) {
        int i = 0;
        while (i < area.length) {
            this.setUncertainty(area[i], ds);
            ++i;
        }
    }

    private void setUncertainty(XDX xdx, String ds) {
        this.setSDS(xdx);
        xdx.dsu = ds;
        xdx.dsl = ds;
        if (!Str.isNumeric(ds)) {
            xdx.dxl = -1.0;
            xdx.dxu = -1.0;
            if (ds.contains("+") && ds.contains("-")) {
                SDS2XDX sx = new SDS2XDX("100", ds);
                xdx.dxl = sx.dxl();
                xdx.dxu = sx.dxu();
            }
        } else {
            xdx.dxu = xdx.dxl = Double.parseDouble(ds);
        }
    }

    private XDX makeXDX(XDX xd, XDX xdu, XDX xdl) {
        if (xdu.x < xd.x || xdl.x > xd.x) {
            return xd.clone();
        }
        double du = xdu.x - xd.x;
        double dl = xd.x - xdl.x;
        double ax = Math.abs(xd.x());
        double au = Math.abs(du);
        double al = Math.abs(dl);
        if (du * dl < 0.0) {
            if (au < 0.1 * ax && al < 0.1 * ax) {
                du = Math.max(au, al);
                return new XDX(xd.x, du, du);
            }
            return XDX.ZERO(-1.0);
        }
        if (du <= 0.0 && dl <= 0.0) {
            double temp = du;
            du = -dl;
            dl = -temp;
        }
        XDX out = new XDX(xd.x, du, dl);
        this.checkNegativeUncertainty(out);
        if (!Str.isNumeric(xd.dsl) && xd.dsl.length() > 0) {
            out.dsl = xd.dsl;
            out.dsu = xd.dsu;
            out.dxl = -1.0;
            out.dxu = -1.0;
        } else if (xd.dxu >= 0.0) {
            out.dxu = Math.sqrt(out.dxu * out.dxu + xd.dxu * xd.dxu);
            out.dxl = Math.sqrt(out.dxl * out.dxl + xd.dxl * xd.dxl);
        }
        this.setSDS(out);
        return out;
    }

    private void checkNegativeUncertainties(XDX[] area) {
        int i = 0;
        while (i < area.length) {
            this.checkNegativeUncertainty(area[i]);
            ++i;
        }
    }

    private void setSDS(XDX xdx) {
        try {
            xdx.s = String.valueOf(xdx.x);
            if (xdx.dxu >= 0.0) {
                xdx.dsu = String.valueOf(xdx.dxu);
                xdx.dsl = String.valueOf(xdx.dxl);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private void checkNegativeUncertainty(XDX xdx) {
        xdx.s = String.valueOf(xdx.x);
        double dxl = xdx.dxl;
        double dxu = xdx.dxu;
        if (dxl < 0.0 && dxu < 0.0) {
            xdx.dxl = -dxu;
            xdx.dxu = -dxl;
            xdx.dsu = String.valueOf(xdx.dxu);
            xdx.dsl = String.valueOf(xdx.dxl);
        } else if (dxl < 0.0) {
            xdx.dxl = 0.0;
        } else if (dxu < 0.0) {
            xdx.dxu = 0.0;
        }
        xdx.dsl = String.valueOf(xdx.dxl);
        xdx.dsu = String.valueOf(xdx.dxu);
        if (xdx.dxl == 0.0) {
            xdx.dsl = "0";
        }
        if (xdx.dxu == 0.0) {
            xdx.dsu = "0";
        }
    }

    public void addPoint(DataPoint2D p) {
        this.addPoint(p, this.data);
    }

    public void addPoint(double x, double y) {
        this.addPoint(new DataPoint2D(x, y));
    }

    public void addPoint(DataPoint2D p, Vector<DataPoint2D> pointsV) {
        int index = Util.findIndexToInsert(p.x, pointsV);
        pointsV.add(index, p);
    }

    private void addCalculatedFermi(DataPoint2D p) {
        if (this.calculatedFermiV.size() == 0) {
            this.calculatedFermiV.add(p);
            return;
        }
        int index = Util.findIndexToInsert(p.x, this.calculatedFermiV);
        if (index < this.calculatedFermiV.size() && this.calculatedFermiV.get((int)index).x == p.x) {
            return;
        }
        if (index > 0 && this.calculatedFermiV.get((int)(index - 1)).x == p.x) {
            return;
        }
        if (index < this.calculatedFermiV.size() - 1 && this.calculatedFermiV.get((int)(index + 1)).x == p.x) {
            return;
        }
        this.calculatedFermiV.add(index, p);
    }

    private void addCalculatedIBStren(double x, double y) {
        this.addCalculatedIBStren(new DataPoint2D(x, y));
    }

    private void addCalculatedIBStren(DataPoint2D p) {
        if (this.calculatedIBStrenFuncV.size() == 0) {
            this.calculatedIBStrenFuncV.add(p);
            return;
        }
        int index = Util.findIndexToInsert(p.x, this.calculatedIBStrenFuncV);
        if (index < this.calculatedIBStrenFuncV.size() && this.calculatedIBStrenFuncV.get((int)index).x == p.x) {
            return;
        }
        if (index > 0 && this.calculatedIBStrenFuncV.get((int)(index - 1)).x == p.x) {
            return;
        }
        if (index < this.calculatedIBStrenFuncV.size() - 1 && this.calculatedIBStrenFuncV.get((int)(index + 1)).x == p.x) {
            return;
        }
        this.calculatedIBStrenFuncV.add(index, p);
    }

    private void addCalculatedFermi(double x, double y) {
        this.addCalculatedFermi(new DataPoint2D(x, y));
    }

    public double getIBStrenFunc(double ekeV) {
        return this.getIBStrenFunc0(ekeV / 510.99906);
    }

    private double getIBStrenFunc0(double e) {
        double v = -1.0;
        v = this.IBStrenFuncInterpolation(e, this.precisionIBStrenFunc);
        if (v < 0.0) {
            v = this.IBStrenFuncCalculation0(e);
            this.addCalculatedIBStren(e, v);
        }
        return v;
    }

    private double IBStrenFuncCalculation0(double e) {
        double maxE = this.W0 - 1.0;
        if (e >= maxE || e <= 0.0 || maxE <= 0.0) {
            return 0.0;
        }
        if (!this.hasCalculatedBeta) {
            this.Fermi = W -> this.getFermiFunc0(W, this.precisionFermiBeta);
            this.totalArea.x = this.romb(this.Fermi, 1.0, this.W0, this.precisionRombBeta);
        }
        if (this.totalArea.x <= 0.0) {
            return 0.0;
        }
        Function1D phiFermi = W -> {
            double p = this.phi(W, e);
            if (p == 0.0) {
                return 0.0;
            }
            return this.getFermiFunc0(W, this.precisionFermiIB) * p;
        };
        double v = this.rombOpen(phiFermi, 1.0 + e, this.W0, this.precisionRombOpenIB);
        return v;
    }

    private double phi(double WE, double eIB) {
        if (eIB <= 0.0 || WE <= eIB) {
            return 0.0;
        }
        double W = WE - eIB;
        double p = 0.0;
        double pE = 0.0;
        if (W > 1.0) {
            p = Math.sqrt(W * W - 1.0);
        }
        if (WE > 1.0) {
            pE = Math.sqrt(WE * WE - 1.0);
        }
        try {
            double v = (WE * WE + W * W) * Math.log(W + p) / WE - 2.0 * p;
            return v /= pE * eIB;
        }
        catch (Exception exception) {
            return 0.0;
        }
    }

    public double IBStrenFuncCalculation(double ekeV) {
        return this.IBStrenFuncCalculation0(ekeV / 510.99906);
    }

    private double IBStrenFuncInterpolation(double e, double precision) {
        int nStepMax = 6;
        return this.FuncInterpolation(e, this.calculatedIBStrenFuncV, nStepMax, precision);
    }

    private double getFermiFunc0(double W, double extrapolationPrecision) {
        double v = -1.0;
        v = this.FermiFuncInterpolation(W, extrapolationPrecision);
        if (v < 0.0) {
            v = this.FermiFuncCalculation0(W);
            this.addCalculatedFermi(W, v);
        }
        return v;
    }

    public double getFermiFunc(double WkeV, double extrapolationPrecision) {
        return this.getFermiFunc0(WkeV / 510.99906 + 1.0, extrapolationPrecision);
    }

    private double FermiFuncCalculation0(double W) {
        double p;
        if (W <= 1.0 || W >= this.W0 || this.isECBP && W < 1.001) {
            return 0.0;
        }
        double Wp = W + this.screeningCorrection.v(W);
        double y = (double)this.Z * 0.007297735308 * Wp / (p = Math.sqrt(Wp * Wp - 1.0));
        if (y > 10.0 && this.isECBP) {
            return 0.0;
        }
        if (this.isBetaM && (Wp <= 1.0001 || y > 10.0)) {
            return this.lowEnergyFermiFunc(W, Wp);
        }
        double v = p * Wp * Math.pow(this.W0 - W, 2.0) * this.shapeFactor0(W);
        return v;
    }

    public double FermiFuncCalculation(double WkeV) {
        return this.FermiFuncCalculation0(WkeV / 510.99906 + 1.0);
    }

    private double lowEnergyFermiFunc(double W, double Wp) {
        double v = 0.0;
        double gammaK = 0.0;
        double A = 0.0;
        double B = 0.0;
        double C = 0.0;
        int n = this.nForbiddenUnique;
        double[] corrFactors = new double[n + 1];
        Arrays.fill(corrFactors, 1.0);
        corrFactors[0] = corrFactors[0] + this.nuclearSizeCorrFactor.v(W);
        int k = 1;
        while (k <= n + 1) {
            gammaK = Math.sqrt((double)(k * k) - 5.3256940625629856E-5 * (double)this.Z * (double)this.Z);
            A = Math.pow(Util.doublefact(2 * k - 1), 2.0) * Math.pow(0.014595470616 * (double)this.Z * this.R, 2.0 * gammaK - 1.0);
            B = (double)(Util.fact(2 * k - 1) * Util.fact(2 * (n - k + 1) + 1)) * Math.pow(this.R, 2 * k - 1);
            C = (gammaK + (double)k) * ((double)k - (double)(2 * k + 1) * (0.014595470616 * (double)this.Z * this.R) / (2.0 * gammaK + 1.0));
            v += corrFactors[k - 1] * (A *= Math.pow(this.W0 - W, 2 * (n - k + 2))) * C / (B *= Math.pow(Util.gamma(2.0 * gammaK + 1.0), 2.0));
            ++k;
        }
        v = Math.PI * 2 * Wp * v;
        return v;
    }

    private double FermiFuncInterpolation(double W, double precision) {
        int nStepMax = 6;
        return this.FuncInterpolation(W, this.calculatedFermiV, nStepMax, precision);
    }

    private double FuncInterpolation(double W, Vector<DataPoint2D> dataPointsV, int nStepMax, double precision) {
        double v = -1.0;
        int nCalc = dataPointsV.size();
        if (nCalc < 3 || nCalc > 200) {
            return v;
        }
        double[] yDy = Util.polynomialInterpolation(W, dataPointsV, nStepMax);
        double y = Math.abs(yDy[0]);
        double dy = Math.abs(yDy[1]);
        if (dy >= 0.0 && dy <= y * precision) {
            return y;
        }
        return v;
    }

    private double shapeFactor0(double W) {
        double s = 1.0;
        int n = this.nForbiddenUnique;
        double Wp = W + this.screeningCorrection.v(W);
        double p = Math.sqrt(Wp * Wp - 1.0);
        double[] corrFactors = new double[n + 1];
        Arrays.fill(corrFactors, 1.0);
        corrFactors[0] = corrFactors[0] + this.nuclearSizeCorrFactor.v(W);
        double v = 0.0;
        int k = 1;
        while (k <= n + 1) {
            double temp = this.lambda(k, Wp) * corrFactors[k - 1] * Math.pow(p, 2 * k - 2) * Math.pow(this.W0 - W, 2 * (n - k + 1));
            v += (temp /= (double)(Util.fact(2 * k - 1) * Util.fact(2 * (n - k + 1) + 1)));
            ++k;
        }
        if (v > 0.0) {
            s = v;
        }
        return s;
    }

    public double shapeFactor(double WkeV) {
        return this.shapeFactor0(WkeV / 510.99906 + 1.0);
    }

    private double lambda(int k, double W) {
        double v = 1.0;
        double p = Math.sqrt(W * W - 1.0);
        double f = Math.pow((double)Util.doublefact(2 * k - 1) / Math.pow(p * this.R, k - 1), 2.0);
        double f_k_SQ = this.fkSQ(k, W);
        double g_mk_SQ = this.gkSQ(-k, W);
        v = (f /= 2.0 * p * p) * (f_k_SQ + g_mk_SQ);
        return v;
    }

    private Complex QSF(int k, double W) {
        Complex v = Complex.ONE;
        double p = Math.sqrt(W * W - 1.0);
        double y = (double)this.Z * 0.007297735308 * W / p;
        y = (double)(-this.eZ) * y;
        double gammaK = Math.sqrt((double)(k * k) - 5.3256940625629856E-5 * (double)this.Z * (double)this.Z);
        double Qk = Math.pow(2.0 * p * this.R, gammaK) * Math.exp(Math.PI * y / 2.0) / (2.0 * this.R * Math.sqrt(W));
        Complex temp = new Complex(gammaK, y);
        temp = Util.gamma(temp);
        Qk = Qk * temp.abs() / Util.gamma(2.0 * gammaK + 1.0);
        Complex Sk = new Complex(0.0, -p * this.R);
        Sk = Sk.exp().multiply(new Complex(gammaK, y));
        temp = new Complex(-k, y / W);
        temp = temp.divide(new Complex(gammaK, y));
        temp = temp.sqrt();
        Sk = Sk.multiply(temp);
        Complex a = new Complex(gammaK + 1.0, y);
        Complex b = new Complex(2.0 * gammaK + 1.0, 0.0);
        Complex c = new Complex(0.0, 2.0 * p * this.R);
        Complex F1 = Util.hyperG(a, b, c);
        v = Sk.multiply(F1).multiply(Qk);
        return v;
    }

    private Complex[] fg(int k, double W) {
        Complex[] out = new Complex[2];
        Complex qsf = this.QSF(k, W);
        double temp = qsf.getImaginary();
        Complex tempC = new Complex(1.0 - W, 0.0);
        tempC = tempC.sqrt();
        out[0] = tempC = tempC.multiply(new Complex(0.0, 2.0 * temp));
        temp = qsf.getReal();
        tempC = new Complex(1.0 + W, 0.0);
        tempC = tempC.sqrt();
        out[1] = tempC = tempC.multiply(2.0 * temp);
        return out;
    }

    private double[] fgSQ(int k, double W) {
        double[] out = new double[2];
        Complex qsf = this.QSF(k, W);
        double temp = qsf.getImaginary();
        out[0] = (W - 1.0) * 4.0 * temp * temp;
        temp = qsf.getReal();
        out[1] = (1.0 + W) * 4.0 * temp * temp;
        return out;
    }

    private Complex fk(int k, double W) {
        return this.fg(k, W)[0];
    }

    private Complex gk(int k, double W) {
        return this.fg(k, W)[1];
    }

    private double fkSQ(int k, double W) {
        return this.fgSQ(k, W)[0];
    }

    private double gkSQ(int k, double W) {
        return this.fgSQ(k, W)[1];
    }

    public double totalEndPointEnergy() {
        return this.W0;
    }

    public static double screeningCorrection(int Z, double W, String type) {
        double correction = 0.0;
        if (type.equals("B-")) {
            return DecaySpectrum.screeningCorrectionBetaM(Z);
        }
        if (type.equals("B+")) {
            return DecaySpectrum.screeningCorrectionBetaP(Z, W);
        }
        return correction;
    }

    public static double screeningCorrectionBetaP(int Z, double W) {
        double v = ((-9.45E-9 * (double)Z + 3.014E-6) * (double)Z + 1.881E-4) * (double)Z - 5.166E-4;
        double a = ((1.11E-7 * (double)Z - 1.01E-5) * (double)Z - 0.00238) * (double)Z + 0.102;
        double b = ((-2.42E-8 * (double)Z + 3.83E-6) * (double)Z + 3.6E-5) * (double)Z - 0.0156;
        double p = Math.sqrt(W * W - 1.0);
        return v *= Math.exp(a / p + b / (W * W - 1.0));
    }

    public static double screeningCorrectionBetaM(int Z) {
        double v = ((-9.45E-9 * (double)Z + 3.014E-6) * (double)Z + 1.881E-4) * (double)Z - 5.166E-4;
        return -v;
    }

    public static double nuclearSizeCorrFactor(int Z, double W, String type) {
        double corrFactor = 0.0;
        if (type.equals("B-")) {
            return DecaySpectrum.nuclearSizeCorrFactorBetaM(Z, W);
        }
        if (type.equals("B+")) {
            return DecaySpectrum.nuclearSizeCorrFactorBetaP(Z, W);
        }
        return corrFactor;
    }

    public static double nuclearSizeCorrFactorBetaM(int Z, double W) {
        if (Z > 50) {
            return (double)(Z - 50) * (-0.0025 - 4.0E-6 * W * (double)(Z - 50));
        }
        return 0.0;
    }

    public static double nuclearSizeCorrFactorBetaP(int Z, double W) {
        if (Z > 80) {
            return (double)(Z - 80) * (-1.7E-4 * W + 6.3E-4 / W - 0.0088 / (W * W));
        }
        return 0.0;
    }

    private double romb(Function1D f, double a, double b, double precision) {
        return Integration.romb(f, a, b, precision);
    }

    private double rombOpen(Function1D f, double a, double b, double precision) {
        return Integration.rombOpen(f, a, b, precision);
    }

    public String name() {
        return this.name;
    }

    public void setName(String s) {
        this.name = s;
    }

    public String NUCID() {
        return this.NUCID;
    }

    public int nPoints() {
        return this.data.size();
    }

    public DataPoint2D pointAt(int i) {
        try {
            return this.data.get(i);
        }
        catch (Exception exception) {
            return null;
        }
    }

    public Vector<DecaySpectrum> alternativeSpecsV() {
        return this.alternativeSpecsV;
    }

    public DataPoint2D calculatedFermiPointAt(int i) {
        try {
            return this.calculatedFermiV.get(i);
        }
        catch (Exception exception) {
            return null;
        }
    }

    public double[] energies() {
        double[] out = new double[this.data.size()];
        int i = 0;
        while (i < this.data.size()) {
            out[i] = this.data.get((int)i).x * 510.99906;
            ++i;
        }
        return out;
    }

    public double[] intensities() {
        double[] out = new double[this.data.size()];
        int i = 0;
        while (i < this.data.size()) {
            out[i] = this.data.get((int)i).y;
            ++i;
        }
        return out;
    }

    public void setGrid(float[] gridE, float[] gridEIB) {
        this.setGridE(gridE);
        this.setGridEIB(gridEIB);
    }

    public void setGridE(float[] gridE) {
        this.gridE = (float[])gridE.clone();
        this.E = new double[gridE.length];
        int i = 0;
        while (i < gridE.length) {
            this.E[i] = (double)gridE[i] / 510.99906 + 1.0;
            ++i;
        }
    }

    public float[] gridE() {
        return this.gridE;
    }

    public float gridE(int i) {
        return Util.getValue(this.gridE, i);
    }

    public void setGridEIB(float[] gridEIB) {
        this.gridEIB = (float[])gridEIB.clone();
        this.EIB = new double[gridEIB.length];
        int i = 0;
        while (i < gridEIB.length) {
            this.EIB[i] = (double)gridEIB[i] / 510.99906;
            ++i;
        }
    }

    public float[] gridEIB() {
        return this.gridEIB;
    }

    public float gridEIB(int i) {
        return Util.getValue(this.gridEIB, i);
    }

    public XDX[] area() {
        return this.area;
    }

    public XDX area(int i) {
        return Util.getXDX(this.area, i);
    }

    public XDX totalArea() {
        return this.totalArea;
    }

    public XDX[] Earea() {
        return this.EareaInkeV;
    }

    public XDX Earea(int i) {
        return Util.getXDX(this.EareaInkeV, i);
    }

    public XDX[] areaIBbeta() {
        return this.areaIBbeta;
    }

    public XDX areaIBbeta(int i) {
        return Util.getXDX(this.areaIBbeta, i);
    }

    public XDX[] EareaIBbeta() {
        return this.EareaIBbetaInkeV;
    }

    public XDX EareaIBbeta(int i) {
        return Util.getXDX(this.EareaIBbetaInkeV, i);
    }

    public XDX[] areaIBec() {
        return this.areaIBec;
    }

    public XDX areaIBec(int i) {
        return Util.getXDX(this.areaIBec, i);
    }

    public XDX[] EareaIBec() {
        return this.EareaIBecInkeV;
    }

    public XDX EareaIBec(int i) {
        return Util.getXDX(this.EareaIBecInkeV, i);
    }

    public XDX averageEnergy() {
        return this.averageTotalEnergy.subtract(XDX.ONE()).multiply(510.99906);
    }

    public boolean hasInputFeeding() {
        return this.hasInputFeeding;
    }

    public void setHasInputFeeding(boolean b) {
        this.hasInputFeeding = b;
    }

    public boolean isDataEmpty() {
        return this.data.isEmpty();
    }

    public int nForbiddenUnique() {
        return this.nForbiddenUnique;
    }

    public int nForbidden() {
        return this.nForbidden;
    }

    public JPI deltaJPI() {
        return this.deltaJPI;
    }

    public boolean isUnique() {
        return this.isUnique;
    }

    public void setIsUnique(boolean b) {
        this.isUnique = b;
    }

    public boolean isNonUnique() {
        return this.isNonUnique;
    }

    public boolean isAllowed() {
        return this.isAllowed;
    }

    public void setParent(Parent p) {
        this.parent = p;
    }

    public Parent parent() {
        return this.parent;
    }

    public String decayType() {
        return this.decayType;
    }

    public void setDecayType(String type) {
        this.decayType = type;
    }

    public void setEndPointEnergy(double W0keV) {
        this.W0keV = W0keV;
        this.W0 = W0keV / 510.99906;
    }

    public double endPointEnergy() {
        return this.W0keV;
    }

    public String getMessage() {
        return this.message;
    }

    public void clearMessage() {
        this.message = "";
    }

    public XDX partialT12() {
        return this.pT12;
    }

    public XDX fTotal() {
        return this.fTotal;
    }

    public XDX fB() {
        return this.fB;
    }

    public XDX fEC() {
        return this.fEC;
    }

    public XDX fK() {
        return this.fK;
    }

    public XDX fL() {
        return this.fL;
    }

    public XDX fMNO() {
        return this.fMNO;
    }

    public XDX ft() {
        return this.ft;
    }

    public XDX logf() {
        return this.logf;
    }

    public XDX logft() {
        return this.logft;
    }

    public XDX ratioEC2BP() {
        return this.rEC2BP;
    }

    public XDX ratioK2ECBP() {
        return this.rK2ECBP;
    }

    public XDX ratioL2ECBP() {
        return this.rL2ECBP;
    }

    public XDX ratioMNO2ECBP() {
        return this.rMNO2ECBP;
    }

    public XDX calcIBeta() {
        return this.calcIBeta;
    }

    public XDX calcIEC() {
        return this.calcIEC;
    }

    public XDX AIB() {
        return this.AIB;
    }

    public XDX AIE() {
        return this.AIE;
    }

    public XDX AIT() {
        return this.AIT;
    }

    public XDX decayIT() {
        return this.decayIT;
    }

    public XDX decayBR() {
        return this.decayBR;
    }

    public boolean hasIB() {
        return this.hasIB;
    }

    public boolean hasIE() {
        return this.hasIE;
    }

    public boolean hasIT() {
        return this.hasIT;
    }

    public DecaySpectrum spec0() {
        return this.spec0;
    }

    private static interface Integrate {
        public double doIntegrate(Function1D var1, double var2, double var4);
    }
}
