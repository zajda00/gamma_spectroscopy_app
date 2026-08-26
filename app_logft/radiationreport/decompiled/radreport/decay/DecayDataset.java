/*
 * Decompiled with CFR 0.152.
 */
package radreport.decay;

import ensdfparser.calc.UncertaintyParser;
import ensdfparser.calc.XDX;
import ensdfparser.ensdf.Alpha;
import ensdfparser.ensdf.Beta;
import ensdfparser.ensdf.CE;
import ensdfparser.ensdf.DParticle;
import ensdfparser.ensdf.Decay;
import ensdfparser.ensdf.ECBP;
import ensdfparser.ensdf.ENSDF;
import ensdfparser.ensdf.Gamma;
import ensdfparser.ensdf.Level;
import ensdfparser.ensdf.Normal;
import ensdfparser.ensdf.Nucleus;
import ensdfparser.ensdf.Parent;
import ensdfparser.ensdf.SDS2XDX;
import ensdfparser.ensdf.XDX2SDS;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.util.Str;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Vector;
import radreport.data.AtomicEntry;
import radreport.decay.AbstractDecayDataset;
import radreport.decay.DecaySpectrum;
import radreport.decay.RADConfig;
import radreport.decay.RADControl;
import radreport.decay.Radiation;
import radreport.decay.StringWriter;
import radreport.math.Util;

public class DecayDataset
extends AbstractDecayDataset {
    ENSDF ens = null;
    int A = -1;
    int Z = -1;
    int AP = -1;
    int ZP = -1;
    int eZ = -1;
    Nucleus nucleus = null;
    Parent parent = null;
    String NUCID = "";
    String decayType = "";
    double R = 0.0;
    Vector<DecaySpectrum> spectraV = new Vector();
    float[] gridEB;
    float[] gridEIB;
    double maxEndPoint = 0.0;
    boolean isBetaM = false;
    boolean isECBP = false;
    boolean isAlpha = false;
    boolean isDelayed = false;
    String betaType = "";
    String dpType = "";
    int nECWithBP = 0;
    XDX Q;
    XDX BR;
    XDX mass;
    XDX massP;
    XDX massRatio;
    XDX dpBR;
    LinkedHashMap<String, Radiation> xrayMap = new LinkedHashMap();
    LinkedHashMap<String, Radiation> AugerMap = new LinkedHashMap();
    LinkedHashMap<Decay, DecaySpectrum> decaySpectrumMap = new LinkedHashMap();
    boolean isValid = false;
    boolean hasParent = false;
    boolean hasNorm = false;
    AtomicEntry atomic = null;
    AtomicEntry atomicP = null;
    private String indent = RADControl.indent;
    private String message = "";
    GetDecayEnergy getDecayE;
    AbsoluteRI absRI0 = null;
    AbsoluteRI absRI1 = null;
    GetEAV EAV = null;

    public DecayDataset(ENSDF ens) {
        this.reset();
        this.ens = ens;
        this.nucleus = ens.nucleus();
        this.NUCID = this.nucleus.nameENSDF().trim();
        this.A = this.nucleus.A();
        this.Z = this.nucleus.Z();
        this.R = Util.radius(this.A);
        this.mass = RADConfig.getMass(this.NUCID);
        this.atomic = RADConfig.getAtomic(this.Z);
        if (this.atomic == null) {
            this.atomic = new AtomicEntry();
        }
        this.massRatio = this.ZERO();
        if (ens.nParents() > 0) {
            this.parent = ens.parentAt(0);
            this.hasParent = true;
            Nucleus nucP = this.parent.nucleus();
            this.AP = nucP.A();
            this.ZP = nucP.Z();
            this.massP = RADConfig.getMass(nucP.nameENSDF().trim());
            this.atomicP = RADConfig.getAtomic(this.ZP);
            if (this.mass != null && this.massP != null) {
                this.massRatio = this.mass.divided(this.massP, true);
            }
        } else {
            return;
        }
        this.dpType = ens.DPType().trim().toUpperCase();
        this.decayType = ens.decayTypeInDSID();
        this.betaType = this.decayType.contains("EC") || this.decayType.contains("B+") ? "B+" : (this.decayType.contains("B-") ? "B-" : "");
        if (this.decayType.isEmpty()) {
            return;
        }
        if (!this.dpType.isEmpty()) {
            this.isDelayed = true;
        }
        UncertaintyParser.setDRIfactorForEmpty(0.0);
        UncertaintyParser.setDRIfactorForAP(0.0);
        UncertaintyParser.setDRIfactorForGT(0.0);
        UncParser uncParser0 = (s, ds) -> UncertaintyParser.findParsedXDX(s, ds);
        UncParser uncParser1 = (s, ds) -> UncertaintyParser.findParsedXDX(s, ds, 'A');
        this.absRI0 = (a, da, s, ds, flag) -> {
            double dau = da;
            double dal = da;
            if (!(da > 0.0)) {
                if (Str.isLetters(ds) || ds.isEmpty()) {
                    XDX xdx = uncParser0.get(s, ds);
                    a = xdx.x;
                    da = xdx.dxu;
                    if (da < 0.0) {
                        da = 0.0;
                    }
                    dau = da;
                    dal = xdx.dxl;
                    if (dal < 0.0) {
                        dal = 0.0;
                    }
                } else if (ds.contains("+") && ds.contains("-")) {
                    SDS2XDX sx = new SDS2XDX(s, ds);
                    a = sx.x();
                    dau = sx.dxu();
                    dal = sx.dxl();
                    if (dau < 0.0) {
                        dau = 0.0;
                    }
                    if (dal < 0.0) {
                        dal = 0.0;
                    }
                }
            }
            if (flag == 'U' || flag == 'u') {
                dau = da = (a = (a + da) / 2.0);
                dal = da;
            }
            return new XDX(a, dau, dal);
        };
        this.absRI1 = (a, da, s, ds, flag) -> {
            double dau = da;
            double dal = da;
            if (!(da > 0.0)) {
                if (Str.isLetters(ds) || ds.isEmpty()) {
                    XDX xdx = uncParser1.get(s, ds);
                    a = xdx.x;
                    da = xdx.dxu;
                    if (da < 0.0) {
                        da = 0.0;
                    }
                    dau = da;
                    dal = xdx.dxl;
                    if (dal < 0.0) {
                        dal = 0.0;
                    }
                } else if (ds.contains("+") && ds.contains("-")) {
                    SDS2XDX sx = new SDS2XDX(s, ds);
                    a = sx.x();
                    dau = sx.dxu();
                    dal = sx.dxl();
                    if (dau < 0.0) {
                        dau = 0.0;
                    }
                    if (dal < 0.0) {
                        dal = 0.0;
                    }
                }
            }
            if (flag == 'U' || flag == 'u') {
                dau = da = (a = (a + da) / 2.0);
                dal = da;
            }
            return new XDX(a, dau, dal);
        };
        this.setupDecays();
        this.EAV = d -> new XDX(d.EAvF(), d.DEAvF());
        if (this.betaType.length() > 0) {
            if (RADControl.calculateContinua) {
                boolean b = RADControl.calculateBremsstrahlung;
                RADControl.calculateBremsstrahlung = false;
                String msg = this.message = String.valueOf(this.message) + "*** EAV will be re-calculated for use in beta radiation calculation ***\n\n";
                this.calculateContinua();
                this.EAV = d -> {
                    XDX out = new XDX(-1.0, 0.0);
                    try {
                        int index = this.decaysV.indexOf(d);
                        DecaySpectrum spec = this.spectrumAt(index);
                        return spec.averageEnergy();
                    }
                    catch (Exception exception) {
                        return out;
                    }
                };
                this.message = msg;
                RADControl.calculateBremsstrahlung = b;
            } else {
                this.message = String.valueOf(this.message) + "*** EAV in file will be used in beta radiation calculation ***\n";
                this.message = String.valueOf(this.message) + "    Decay with no EAV in file will be skipped in calculation\n\n";
            }
        }
        this.calculateRadiations();
    }

    public void reset() {
        super.init();
    }

    private void setupDecays() {
        Radiation rad;
        SDS2XDX sx;
        if (this.ens.norm().normExists()) {
            this.hasNorm = true;
        }
        this.isValid = this.hasParent && this.hasNorm;
        Normal norm = this.ens.norm();
        double br = norm.BRD();
        double dbr = norm.DBRD();
        String brs = norm.BRS();
        String dbrs = norm.DBRS();
        if (brs.isEmpty()) {
            br = 1.0;
            dbr = 0.0;
            brs = "1";
            dbrs = "";
        }
        String nbs = norm.NBS();
        String dnbs = norm.DNBS();
        String nbbrs = norm.NBBRS();
        String dnbbrs = norm.DNBBRS();
        if (dbrs.isEmpty() && norm.isBRUnity()) {
            dbrs = "0";
        }
        if (dnbs.isEmpty() && norm.isNBUnity()) {
            dnbs = "0";
        }
        if (dnbbrs.isEmpty() && norm.isBMRenUnity()) {
            dnbbrs = "0";
        }
        if (nbbrs.isEmpty()) {
            SDS2XDX sx1 = new SDS2XDX(brs, dbrs);
            Iterator<Level> sx2 = new SDS2XDX(nbs, dnbs);
            sx = sx1.multiply((SDS2XDX)((Object)sx2));
        } else {
            sx = new SDS2XDX(nbbrs, dnbbrs);
        }
        XDX nbbr = Str.isNumeric(sx.ds()) ? new XDX(sx.x(), sx.dxu(), sx.dxl()) : new XDX(sx.s(), sx.dsu(), sx.dsl());
        if (dbrs.startsWith("G") && br < 1.0) {
            br = (br + 1.0) / 2.0;
            dbr = (1.0 - br) / 2.0;
            this.BR = new XDX(br, dbr);
        } else {
            this.BR = this.absRI0.get(br, dbr, brs, dbrs, ' ');
        }
        this.dpBR = this.ZERO();
        if (this.parent != null) {
            double q = this.parent.QF();
            double dq = this.parent.DQF();
            String QS = this.parent.QS();
            String DQS = this.parent.DQS();
            this.Q = this.absRI0.get(q, dq, QS, DQS, ' ');
            this.QBR = this.Q.multiply(this.BR);
        }
        this.nECWithBP = this.ens.nECBPWIB();
        if (this.atomic == null) {
            this.atomic = new AtomicEntry();
        }
        if (this.mass == null) {
            return;
        }
        this.AugersV.clear();
        this.xraysV.clear();
        if (this.atomic.aeL != null && this.atomic.aeL.x > 0.0) {
            rad = new Radiation("AUGER", "L");
            rad.setEnergy((float)this.atomic.aeL.x, (float)this.atomic.aeL.dxu);
            rad.setElement(this.nucleus.EN());
            rad.setZ(this.Z);
            this.AugersV.add(rad);
            this.AugerMap.put(rad.type(), rad);
        }
        if (this.atomic.aeK != null && this.atomic.aeK.x > 0.0) {
            rad = new Radiation("AUGER", "K");
            rad.setEnergy((float)this.atomic.aeK.x, (float)this.atomic.aeK.dxu);
            rad.setElement(this.nucleus.EN());
            rad.setZ(this.Z);
            this.AugersV.add(rad);
            this.AugerMap.put(rad.type(), rad);
        }
        if (this.atomic.xeL != null && this.atomic.xeL.x > 0.0) {
            rad = new Radiation("XRAY", "L");
            rad.setEnergy((float)this.atomic.xeL.x, (float)this.atomic.xeL.dxu);
            rad.setElement(this.nucleus.EN());
            rad.setZ(this.Z);
            this.xraysV.add(rad);
            this.xrayMap.put(rad.type(), rad);
        }
        if (this.atomic.xeKA2 != null && this.atomic.xeKA2.x > 0.0) {
            rad = new Radiation("XRAY", "KA2");
            rad.setEnergy((float)this.atomic.xeKA2.x, (float)this.atomic.xeKA2.dxu);
            rad.setElement(this.nucleus.EN());
            rad.setZ(this.Z);
            this.xraysV.add(rad);
            this.xrayMap.put(rad.type(), rad);
        }
        if (this.atomic.xeKA1 != null && this.atomic.xeKA1.x > 0.0) {
            rad = new Radiation("XRAY", "KA1");
            rad.setEnergy((float)this.atomic.xeKA1.x, (float)this.atomic.xeKA1.dxu);
            rad.setElement(this.nucleus.EN());
            rad.setZ(this.Z);
            this.xraysV.add(rad);
            this.xrayMap.put(rad.type(), rad);
        }
        if (this.atomic.xeKB != null && this.atomic.xeKB.x > 0.0) {
            rad = new Radiation("XRAY", "KB");
            rad.setEnergy((float)this.atomic.xeKB.x, (float)this.atomic.xeKB.dxu);
            rad.setElement(this.nucleus.EN());
            rad.setZ(this.Z);
            this.xraysV.add(rad);
            this.xrayMap.put(rad.type(), rad);
        }
        this.AugersV = Radiation.sort(this.AugersV);
        this.xraysV = Radiation.sort(this.xraysV);
        this.cesV.clear();
        this.gammasV.clear();
        for (Gamma g : this.ens.unpGammas()) {
            this.gammasV.add(g);
            this.unpGammasV.add(g);
            this.cesV.addAll(g.convElecV());
        }
        for (Level lev : this.ens.levelsV()) {
            this.gammasV.addAll(lev.GammasV());
            for (Gamma g : lev.GammasV()) {
                this.cesV.addAll(g.convElecV());
            }
        }
        this.gammasV = ENSDF.sortRecords(this.gammasV);
        this.cesV = CE.sort(this.cesV);
        if (!this.isDelayed) {
            if (this.decayType.equals("B-")) {
                this.isBetaM = true;
                this.getDecayE = (ensdf, l) -> {
                    try {
                        return EnsdfUtil.calculateDecayEnergy(ensdf, l, "PARTICLE");
                    }
                    catch (Exception exception) {
                        return null;
                    }
                };
            } else if ("EC,B+,EC+B+".contains(this.decayType)) {
                this.isECBP = true;
                this.getDecayE = (ensdf, l) -> {
                    XDX2SDS x2s = null;
                    try {
                        x2s = EnsdfUtil.calculateDecayEnergy(ensdf, l, "TOTAL");
                        ECBP ec = (ECBP)l.DecaysV().get(0);
                        ec.setEPEF_EC(x2s.xf());
                        ec.setDEPEF_EC(x2s.dxf());
                        ec.setEPES_EC(x2s.s());
                        ec.setDEPES_EC(x2s.ds());
                        double r = this.massRatio.x;
                        double x = r * (x2s.x() - 1021.99812);
                        double dx = x2s.dx() * r;
                        x2s = new XDX2SDS(x, dx);
                        return x2s;
                    }
                    catch (Exception exception) {
                        return null;
                    }
                };
            } else if (this.decayType.equals("A")) {
                this.isAlpha = true;
                this.getDecayE = (ensdf, l) -> {
                    try {
                        return EnsdfUtil.calculateDecayEnergy(ensdf, l, "PARTICLE");
                    }
                    catch (Exception exception) {
                        return null;
                    }
                };
            }
        }
        int nDecaysWithBeta = 0;
        this.decaysV.clear();
        this.dpsV.clear();
        int i = this.ens.nLevels() - 1;
        while (i >= 0) {
            Level l2 = this.ens.levelAt(i);
            try {
                Decay decay = l2.DecaysV().get(0);
                if (l2.msS().equals("R")) {
                    XDX tempBR = this.ZERO();
                    String s = "";
                    String ds = "";
                    double x = -1.0;
                    double dx = -1.0;
                    if (this.isECBP) {
                        ECBP d = (ECBP)decay;
                        s = d.altITS();
                        ds = d.altDITS();
                        x = d.altDITD();
                        dx = d.altDITD();
                    } else {
                        s = decay.RIS();
                        ds = decay.DRIS();
                        x = decay.ID();
                        dx = decay.DID();
                    }
                    tempBR = Str.isNumeric(ds) ? new XDX(x, dx) : new XDX(s, ds);
                    if (nbbr.x != 1.0) {
                        if (nbbr.dxl < 0.0) {
                            nbbr.dxl = 0.0;
                            nbbr.dxu = 0.0;
                        }
                        tempBR = tempBR.multiply(nbbr);
                    }
                    tempBR = tempBR.divided(100.0);
                    this.dpBR = this.dpBR.add(tempBR);
                }
                this.decaysV.add(decay);
                XDX2SDS x2s = this.getDecayE.get(this.ens, l2);
                double x = x2s.x();
                double dx = x2s.dx();
                if (dx < 0.0) {
                    dx = 0.0;
                }
                String s = x2s.s();
                String ds = x2s.ds();
                if (x > 0.0 && decay.ID() > 0.0) {
                    ++nDecaysWithBeta;
                }
                x2s = new XDX2SDS();
                x2s.setErrorLimit(99);
                x2s.setValues(x, dx);
                s = x2s.s();
                if (x2s.dx() > 0.0 || x2s.ds().contains("+")) {
                    ds = x2s.ds();
                }
                decay.setEF((float)x);
                decay.setDEF((float)dx);
                decay.setES(s);
                decay.setDES(ds);
            }
            catch (Exception exception) {
                // empty catch block
            }
            try {
                this.dpsV.addAll(l2.DParticlesV());
            }
            catch (Exception exception) {
                // empty catch block
            }
            --i;
        }
        this.dpsV.addAll(this.ens.unpDParticles());
        this.decaysV = ENSDF.sortRecords(this.decaysV);
        this.dpsV = ENSDF.sortRecords(this.dpsV);
        if (this.isECBP) {
            this.nECWithBP = nDecaysWithBeta;
        }
        this.gridEB = this.findGridEnergy(RADControl.EB_GRID_STEP0, RADControl.EB_GRID_STEP_MULTIPLIER);
        this.gridEIB = this.findGridEnergy(RADControl.EIB_GRID_STEP0, RADControl.EIB_GRID_STEP_MULTIPLIER);
    }

    private XDX resetQueXDX(XDX xdx) {
        double x = xdx.x;
        double dx = xdx.dxu;
        if (dx < 0.0) {
            dx = 0.0;
        }
        dx = x = (x + dx) / 2.0;
        return new XDX(x, dx);
    }

    public boolean isFakeDecay(Decay d) {
        boolean isFakeDecay = false;
        try {
            Level lev;
            if (d.getFLI() >= 0 && (lev = this.ens.levelAt(d.getFLI())).msS().equals("R")) {
                isFakeDecay = true;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return isFakeDecay;
    }

    private void calculateRadiations() {
        int i;
        Decay d;
        XDX AIB;
        SDS2XDX s2x;
        XDX xdx;
        this.resetOmitted();
        double normG = 1.0;
        double dnormG = 0.0;
        double normB = 1.0;
        double dnormB = 0.0;
        double normA = 1.0;
        double dnormA = 0.0;
        double normDP = 1.0;
        double dnormDP = 0.0;
        String normGS = "";
        String dnormGS = "";
        String normBS = "";
        String dnormBS = "";
        String normAS = "";
        String dnormAS = "";
        String normDPS = "";
        String dnormDPS = "";
        Normal norm = this.ens.norm();
        normG = norm.GRenD();
        dnormG = norm.DGRenD();
        normGS = norm.GRenS();
        dnormGS = norm.DGRenS();
        if (normG <= 0.0) {
            normG = norm.TIRenD();
            dnormG = norm.DTIRenD();
            normGS = norm.TIRenS();
            dnormGS = norm.DTIRenS();
        }
        normB = norm.BMRenD();
        dnormB = norm.DBMRenD();
        normBS = norm.BMRenS();
        dnormBS = norm.DBMRenS();
        normA = norm.BRD();
        dnormA = norm.DBRD();
        normAS = norm.BRS();
        dnormAS = norm.DBRS();
        normDP = norm.NPD();
        dnormDP = norm.DNPD();
        normDPS = norm.NPS();
        dnormDPS = norm.DNPS();
        if (norm.BRS().isEmpty()) {
            normA = 1.0;
            dnormA = 0.0;
            normAS = "1";
            dnormAS = "";
            if (normG < 0.0) {
                normG = norm.NRD();
                dnormG = norm.DNRD();
                normGS = norm.NRS();
                dnormGS = norm.DNRS();
            }
        }
        AbsoluteRI absRI = null;
        absRI = normB < 1.0 ? this.absRI0 : this.absRI1;
        if (normG > 0.0 && dnormG < 0.0) {
            xdx = this.absRI0.get(normG, dnormG, normGS, dnormGS, ' ');
            normG = xdx.x;
            dnormG = xdx.dxu;
        }
        if (normB > 0.0 && dnormB < 0.0) {
            xdx = this.absRI0.get(normB, dnormB, normBS, dnormBS, ' ');
            normB = xdx.x;
            dnormB = xdx.dxu;
        }
        if (normA > 0.0 && dnormA < 0.0) {
            xdx = this.absRI0.get(normA, dnormA, normAS, dnormAS, ' ');
            normA = xdx.x;
            dnormA = xdx.dxu;
        }
        if (dnormDP < 0.0) {
            xdx = this.absRI0.get(normDP, dnormDP, normDPS, dnormDPS, ' ');
            normDP = xdx.x;
            dnormDP = xdx.dxu;
        }
        if (normB < 0.0) {
            normB = 0.0;
        }
        if (normG < 0.0) {
            normG = 0.0;
        }
        if (normA < 0.0) {
            normA = 0.0;
        }
        if (normDP < 0.0) {
            normDP = 0.0;
        }
        Radiation rad = null;
        XDX ieK = this.ZERO();
        XDX ieL = this.ZERO();
        XDX xdx2 = null;
        XDX2SDS x2s = null;
        for (Gamma g : this.gammasV) {
            String flag = String.valueOf(g.flag()) + " ";
            boolean isGamSuppressed = RADControl.isSuppressGamma(g);
            if (g.RIS().isEmpty() && g.TIS().isEmpty()) continue;
            if (g.IG() > 0.0f && g.DIG() > 0.0f) {
                xdx2 = new XDX(g.IG(), g.DIG());
            } else {
                SDS2XDX s2x2 = new SDS2XDX(g.altRIS(), g.altDRIS());
                xdx2 = this.absRI0.get(s2x2.x(), s2x2.dx(), s2x2.s(), s2x2.ds(), flag.charAt(0));
                xdx2 = xdx2.multiply(normG, dnormG);
            }
            if (g.q().equals("?") && !isGamSuppressed && RADControl.resetQueRI) {
                xdx2 = this.resetQueXDX(xdx2);
            }
            x2s = new XDX2SDS(xdx2.x, xdx2.dxu, RADControl.errorLimit);
            g.setAID(xdx2.x);
            g.setDAID(xdx2.dxu);
            g.setAIS(x2s.s());
            g.setDAIS(x2s.ds());
            if (isGamSuppressed || xdx2.x < (double)RADControl.MIN_RI && RADControl.omitWeakRadiation) {
                this.omittedPhotonE = this.omittedPhotonE.add(xdx2.multiply(g.EF(), g.DEF()));
                this.omittedPhotonI = this.omittedPhotonI.add(xdx2);
                this.omittedGammasV.add(g);
                if (isGamSuppressed) {
                    this.suppressedGammasV.add(g);
                }
            } else {
                this.totalGammaE = this.totalGammaE.add(xdx2.multiply(g.EF(), g.DEF()));
                this.totalGammaI = this.totalGammaI.add(xdx2);
            }
            double x = g.EF() * g.EF();
            double dx = 2.0f * g.EF() * g.DEF();
            double massKeV = this.mass.x / 1000000.0 * 931494.32;
            XDX recoilG = xdx2.multiply(x, dx);
            recoilG = recoilG.divided(2.0 * massKeV);
            this.totalRecoilE = this.totalRecoilE.add(recoilG);
            if (g.FLI() < 0) {
                this.totalUnpGammaE = this.totalUnpGammaE.add(xdx2.multiply(g.EF(), g.DEF()));
                this.totalUnpGammaI = this.totalUnpGammaI.add(xdx2);
            }
            for (CE ce : g.convElecV()) {
                String type;
                if (ce.AID() <= 0.0) continue;
                double cc = ce.CC();
                double dcc = ce.DCC();
                if (cc > 0.0 && dcc <= 0.0) {
                    dcc = RADControl.theoryDCC * cc;
                }
                if (g.IG() > 0.0f && g.DIG() > 0.0f) {
                    xdx2 = new XDX(g.IG(), g.DIG());
                } else {
                    SDS2XDX s2x3 = new SDS2XDX(ce.originalIGS(), ce.originalDIGS());
                    xdx2 = this.absRI0.get(s2x3.x(), s2x3.dx(), s2x3.s(), s2x3.ds(), flag.charAt(0));
                    xdx2 = xdx2.multiply(normG, dnormG);
                }
                if (cc > 0.0) {
                    xdx2 = xdx2.multiply(cc, dcc);
                }
                if (g.q().equals("?") && !isGamSuppressed && RADControl.resetQueRI) {
                    xdx2 = this.resetQueXDX(xdx2);
                }
                x2s = new XDX2SDS(xdx2.x, xdx2.dxu, RADControl.errorLimit);
                ce.setAID(xdx2.x);
                ce.setDAID(xdx2.dxu);
                ce.setAIS(x2s.s());
                ce.setDAIS(x2s.ds());
                if (isGamSuppressed || xdx2.x < (double)RADControl.MIN_RI && RADControl.omitWeakRadiation) {
                    this.omittedCEAugerE = this.omittedCEAugerE.add(xdx2.multiply(ce.EF(), ce.DEF()));
                    this.omittedCEAugerI = this.omittedCEAugerI.add(xdx2);
                    this.omittedCEsV.add(ce);
                    if (isGamSuppressed) {
                        this.suppressedCEsV.add(ce);
                    }
                } else {
                    this.totalCeE = this.totalCeE.add(xdx2.multiply(ce.EF(), ce.DEF()));
                    this.totalCeI = this.totalCeI.add(xdx2);
                }
                if ((type = ce.type().toUpperCase().trim()).equals("K")) {
                    ieK = ieK.add(xdx2);
                    continue;
                }
                if (!type.equals("L")) continue;
                ieL = ieL.add(xdx2);
            }
        }
        int nGoodBetas = 0;
        if (this.isDelayed) {
            int i2 = 0;
            while (i2 < this.dpsV.size()) {
                DParticle dp = (DParticle)this.dpsV.get(i2);
                if (!(dp.AID() <= 0.0)) {
                    String flag = String.valueOf(dp.flag()) + " ";
                    boolean isSuppressed = RADControl.isSuppress(dp);
                    s2x = new SDS2XDX(dp.RIS(), dp.DRIS());
                    xdx2 = this.absRI1.get(s2x.x(), s2x.dx(), s2x.s(), s2x.ds(), flag.charAt(0));
                    xdx2 = xdx2.multiply(normDP, dnormDP);
                    if (dp.q().equals("?") && !isSuppressed && RADControl.resetQueRI) {
                        xdx2 = this.resetQueXDX(xdx2);
                    }
                    x2s = new XDX2SDS(xdx2.x, xdx2.dxu, RADControl.errorLimit);
                    dp.setAID(xdx2.x);
                    dp.setDAID(xdx2.dxu);
                    dp.setAIS(x2s.s());
                    dp.setDAIS(x2s.ds());
                    if (isSuppressed || xdx2.x < (double)RADControl.MIN_RI && RADControl.omitWeakRadiation) {
                        this.omittedDelayE = this.omittedDelayE.add(xdx2.multiply(dp.EF(), dp.DEF()));
                        this.omittedDelayI = this.omittedDelayI.add(xdx2);
                        this.omittedDelaysV.add(dp);
                        if (isSuppressed) {
                            this.suppressedDelaysV.add(dp);
                        }
                    } else {
                        this.totalDelayE = this.totalDelayE.add(xdx2.multiply(dp.EF(), dp.DEF()));
                        this.totalDelayI = this.totalDelayI.add(xdx2);
                    }
                }
                ++i2;
            }
        } else if (this.isECBP) {
            double f = 0.0;
            double df = 0.0;
            nGoodBetas = 0;
            int i3 = 0;
            while (i3 < this.decaysV.size()) {
                ECBP d2 = (ECBP)this.decaysV.get(i3);
                if (!(d2.syITD() <= 0.0) && !this.isFakeDecay(d2)) {
                    SDS2XDX sx2;
                    boolean isSuppressed = RADControl.isSuppress(d2);
                    String flag = String.valueOf(d2.flag()) + " ";
                    SDS2XDX s2x4 = new SDS2XDX(d2.altITS(), d2.altDITS());
                    XDX AIT = absRI.get(s2x4.x(), s2x4.dx(), s2x4.s(), s2x4.ds(), flag.charAt(0));
                    AIT = AIT.multiply(normB, dnormB);
                    s2x4 = new SDS2XDX(d2.IBS(), d2.DIBS());
                    AIB = absRI.get(s2x4.x(), s2x4.dx(), s2x4.s(), s2x4.ds(), flag.charAt(0));
                    AIB = AIB.multiply(normB, dnormB);
                    if ((d2.IBS().equals(d2.altITS()) || d2.IBD() == d2.altITD()) && d2.IED() > 0.0) {
                        SDS2XDX sx1 = new SDS2XDX(d2.IBS(), d2.DIBS());
                        sx2 = new SDS2XDX(d2.IES(), d2.DIES());
                        sx1 = sx1.add(sx2);
                        if (Str.isNumeric(d2.altDITS()) == Str.isNumeric(sx1.ds())) {
                            AIT = absRI.get(sx1.x(), sx1.dx(), sx1.s(), sx1.ds(), flag.charAt(0));
                            AIT = AIT.multiply(normB, dnormB);
                        }
                    }
                    s2x4 = new SDS2XDX(d2.IES(), d2.DIES());
                    XDX AIE = absRI.get(s2x4.x(), s2x4.dx(), s2x4.s(), s2x4.ds(), flag.charAt(0));
                    AIE = AIE.multiply(normB, dnormB);
                    if ((d2.IES().equals(d2.altITS()) || d2.IED() == d2.altITD()) && d2.IBD() > 0.0) {
                        SDS2XDX sx1 = new SDS2XDX(d2.IBS(), d2.DIBS());
                        sx2 = new SDS2XDX(d2.IES(), d2.DIES());
                        sx1 = sx1.add(sx2);
                        if (Str.isNumeric(d2.altDITS()) == Str.isNumeric(sx1.ds())) {
                            AIT = absRI.get(sx1.x(), sx1.dx(), sx1.s(), sx1.ds(), flag.charAt(0));
                            AIT = AIT.multiply(normB, dnormB);
                        }
                    }
                    if (d2.IBD() <= 0.0 && d2.IED() <= 0.0 && d2.ITD() > 0.0) {
                        if (d2.EF() <= 50.0f) {
                            AIE = AIT.copy();
                        } else if (d2.EF() >= 9000.0f) {
                            AIB = AIT.copy();
                        }
                    } else if (d2.IBD() <= 0.0 && d2.IED() > 0.0 && d2.ITD() > d2.IED()) {
                        AIB = AIT.subtract(AIE);
                    } else if (d2.IED() <= 0.0 && d2.IBD() > 0.0 && d2.ITD() > d2.IBD()) {
                        AIE = AIT.subtract(AIB);
                    }
                    if (d2.q().equals("?") && !isSuppressed && RADControl.resetQueRI) {
                        AIT = this.resetQueXDX(AIT);
                        AIB = this.resetQueXDX(AIB);
                        AIE = this.resetQueXDX(AIE);
                    }
                    f = d2.CKF();
                    df = d2.DCKF();
                    if (f > 0.0) {
                        if (df <= 0.0) {
                            df = 0.0;
                        }
                        ieK = ieK.add(AIT.multiply(f, df));
                    }
                    f = d2.CLF();
                    df = d2.DCLF();
                    if (f > 0.0) {
                        if (df <= 0.0) {
                            df = 0.0;
                        }
                        ieL = ieL.add(AIT.multiply(f, df));
                    }
                    if (AIT.x > 0.0) {
                        x2s = new XDX2SDS(AIT.x, AIT.dxu, RADControl.errorLimit);
                        d2.setAITD(AIT.x);
                        d2.setDAITD(AIT.dxu);
                        d2.setAITS(x2s.s());
                        d2.setDAITS(x2s.ds());
                    }
                    if (AIB.x > 0.0) {
                        x2s = new XDX2SDS(AIB.x, AIB.dxu, RADControl.errorLimit);
                        d2.setAIBD(AIB.x);
                        d2.setDAIBD(AIB.dxu);
                        d2.setAIBS(x2s.s());
                        d2.setDAIBS(x2s.ds());
                    }
                    if (AIE.x > 0.0) {
                        x2s = new XDX2SDS(AIE.x, AIE.dxu, RADControl.errorLimit);
                        d2.setAIED(AIE.x);
                        d2.setDAIED(AIE.dxu);
                        d2.setAIES(x2s.s());
                        d2.setDAIES(x2s.ds());
                    }
                    if (d2.EPEF_EC() > 0.0f) {
                        if (isSuppressed || AIT.x < (double)RADControl.MIN_RI && RADControl.omitWeakRadiation) {
                            this.omittedDecayE = this.omittedDecayE.add(AIT.multiply(d2.EPEF_EC(), d2.DEPEF_EC()));
                            this.omittedDecayI = this.omittedDecayI.add(AIT);
                            this.omittedDecaysV.add(d2);
                            if (isSuppressed) {
                                this.suppressedDecaysV.add(d2);
                            }
                        } else {
                            this.totalDecayE = this.totalDecayE.add(AIT.multiply(d2.EPEF_EC(), d2.DEPEF_EC()));
                            this.totalDecayI = this.totalDecayI.add(AIT);
                            if (AIE.x > 0.0) {
                                this.totalECE = this.totalECE.add(AIE.multiply(d2.EPEF_EC(), d2.DEPEF_EC()));
                                this.totalECI = this.totalECI.add(AIE);
                            }
                        }
                    }
                    if (d2.EF() > 0.0f && AIB.x > 0.0) {
                        XDX eav = this.EAV.get(d2);
                        if (eav.x <= 0.0) {
                            this.message = String.valueOf(this.message) + "***Warning: EAV is not available at decay of level=" + d2.getFLS() + "\n";
                            this.message = String.valueOf(this.message) + "            Skipped for radiation calculation\n";
                        } else if ((double)d2.EF() >= eav.x) {
                            if (isSuppressed || AIB.x < (double)RADControl.MIN_RI && RADControl.omitWeakRadiation) {
                                this.omittedBetaE = this.omittedBetaE.add(AIB.multiply(eav.x, eav.dxu));
                                this.omittedBetaI = this.omittedBetaI.add(AIB);
                                this.omittedBetasV.add(d2);
                            } else {
                                this.totalBetaE = this.totalBetaE.add(AIB.multiply(eav.x, eav.dxu));
                                this.totalBetaI = this.totalBetaI.add(AIB);
                                this.betasV.add(d2);
                                ++nGoodBetas;
                            }
                            XDX nuE = new XDX(d2.EF(), d2.DEF());
                            nuE = nuE.subtract(eav.x, eav.dxu);
                            this.totalNeutrinoE = this.totalNeutrinoE.add(AIB.multiply(nuE));
                            this.totalNeutrinoI = this.totalNeutrinoI.add(AIB);
                        }
                    }
                    if (AIE.x > 0.0) {
                        XDX nuE = new XDX(d2.EPEF_EC(), d2.DEPEF_EC());
                        this.totalNeutrinoE = this.totalNeutrinoE.add(AIE.multiply(nuE));
                        this.totalNeutrinoI = this.totalNeutrinoI.add(AIE);
                    }
                    if (AIT.x > 0.0) {
                        XDX tempE;
                        if (d2.CKF() > 0.0f) {
                            f = d2.CKF();
                            df = d2.DCKF();
                            if (df < 0.0) {
                                df = 0.0;
                            }
                            tempE = this.atomic.bk.multiply(AIT).multiply(f, df);
                            this.totalNeutrinoE = this.totalNeutrinoE.subtract(tempE);
                            this.totalDecayE = this.totalDecayE.subtract(tempE);
                            if (this.totalECE.x > 0.0) {
                                this.totalECE = this.totalECE.subtract(tempE);
                            }
                        }
                        if (d2.CLF() > 0.0f) {
                            f = d2.CLF();
                            df = d2.DCLF();
                            if (df < 0.0) {
                                df = 0.0;
                            }
                            tempE = this.atomic.bl.multiply(AIT).multiply(f, df);
                            this.totalNeutrinoE = this.totalNeutrinoE.subtract(tempE);
                            this.totalDecayE = this.totalDecayE.subtract(tempE);
                            if (this.totalECE.x > 0.0) {
                                this.totalECE = this.totalECE.subtract(tempE);
                            }
                        }
                        if (d2.CMF() > 0.0f) {
                            XDX be = this.atomic.bm;
                            if (this.atomic.bn.x > 0.0) {
                                double x = (be.x + this.atomic.bn.x) / 2.0;
                                double dx = Math.max(be.dxu, this.atomic.bn.dxu);
                                dx = Math.max(dx, Math.abs(be.x - this.atomic.bn.x));
                                be = new XDX(x, dx);
                            }
                            f = d2.CMF();
                            df = d2.DCMF();
                            if (df < 0.0) {
                                df = 0.0;
                            }
                            XDX tempE2 = be.multiply(AIT).multiply(f, df);
                            this.totalNeutrinoE = this.totalNeutrinoE.subtract(tempE2);
                            this.totalDecayE = this.totalDecayE.subtract(tempE2);
                            if (this.totalECE.x > 0.0) {
                                this.totalECE = this.totalECE.subtract(tempE2);
                            }
                        }
                    }
                }
                ++i3;
            }
            if (this.totalBetaI.x > 0.0) {
                this.annihilationE = this.totalBetaI.multiply(510.99906).multiply(2.0);
                this.annihilationI = this.totalBetaI.multiply(2.0);
                this.totalDecayE = this.totalDecayE.subtract(this.annihilationE);
            }
        } else if (this.isBetaM) {
            nGoodBetas = 0;
            int i4 = 0;
            while (i4 < this.decaysV.size()) {
                d = (Beta)this.decaysV.get(i4);
                if (!(((Beta)d).AID() <= 0.0) && !this.isFakeDecay(d)) {
                    boolean isSuppressed = RADControl.isSuppress(d);
                    String flag = String.valueOf(((Beta)d).flag()) + " ";
                    AIB = null;
                    SDS2XDX s2x5 = new SDS2XDX(((Beta)d).RIS(), ((Beta)d).DRIS());
                    AIB = absRI.get(s2x5.x(), s2x5.dx(), s2x5.s(), s2x5.ds(), flag.charAt(0));
                    AIB = AIB.multiply(normB, dnormB);
                    if (((Beta)d).q().equals("?") && !isSuppressed && RADControl.resetQueRI) {
                        AIB = this.resetQueXDX(AIB);
                    }
                    x2s = new XDX2SDS(AIB.x, AIB.dxu, AIB.dxl, RADControl.errorLimit);
                    ((Beta)d).setAID(AIB.x);
                    if (AIB.dxu > 0.0 && Str.isNumeric(x2s.ds())) {
                        ((Beta)d).setDAID(AIB.dxu);
                    }
                    ((Beta)d).setAIS(x2s.s());
                    ((Beta)d).setDAIS(x2s.ds());
                    if (((Beta)d).EF() > 0.0f) {
                        boolean isOmitted;
                        boolean bl = isOmitted = isSuppressed || AIB.x < (double)RADControl.MIN_RI && RADControl.omitWeakRadiation;
                        if (isOmitted) {
                            this.omittedDecayE = this.omittedDecayE.add(AIB.multiply(((Beta)d).EF(), ((Beta)d).DEF()));
                            this.omittedDecayI = this.omittedDecayI.add(AIB);
                            this.omittedDecaysV.add(d);
                            if (isSuppressed) {
                                this.suppressedDecaysV.add(d);
                            }
                        } else {
                            this.totalDecayE = this.totalDecayE.add(AIB.multiply(((Beta)d).EF(), ((Beta)d).DEF()));
                            this.totalDecayI = this.totalDecayI.add(AIB);
                        }
                        XDX eav = this.EAV.get(d);
                        if (eav.x <= 0.0) {
                            this.message = String.valueOf(this.message) + "***Warning: EAV is not available at decay of level=" + ((Beta)d).getFLS() + "\n";
                            this.message = String.valueOf(this.message) + "            Skipped for radiation calculation\n";
                        } else if ((double)((Beta)d).EF() >= eav.x) {
                            if (isOmitted) {
                                this.omittedBetaE = this.omittedBetaE.add(AIB.multiply(eav.x, eav.dxu));
                                this.omittedBetaI = this.omittedBetaI.add(AIB);
                                this.omittedBetasV.add(d);
                            } else {
                                this.totalBetaE = this.totalBetaE.add(AIB.multiply(eav.x, eav.dxu));
                                this.totalBetaI = this.totalBetaI.add(AIB);
                                this.betasV.add(d);
                                ++nGoodBetas;
                            }
                            XDX nuE = new XDX(((Beta)d).EF(), ((Beta)d).DEF());
                            nuE = nuE.subtract(eav.x, eav.dxu);
                            this.totalNeutrinoE = this.totalNeutrinoE.add(AIB.multiply(nuE));
                            this.totalNeutrinoI = this.totalNeutrinoI.add(AIB);
                        }
                    }
                }
                ++i4;
            }
        } else if (this.isAlpha) {
            int i5 = 0;
            while (i5 < this.decaysV.size()) {
                d = (Alpha)this.decaysV.get(i5);
                if (!(((Alpha)d).AID() <= 0.0) && !this.isFakeDecay(d)) {
                    String flag = String.valueOf(((Alpha)d).flag()) + " ";
                    boolean isSuppressed = RADControl.isSuppress(d);
                    s2x = new SDS2XDX(((Alpha)d).RIS(), ((Alpha)d).DRIS());
                    xdx2 = this.absRI1.get(s2x.x(), s2x.dx(), s2x.s(), s2x.ds(), flag.charAt(0));
                    xdx2 = xdx2.multiply(normA, dnormA);
                    if (((Alpha)d).q().equals("?") && !isSuppressed && RADControl.resetQueRI) {
                        xdx2 = this.resetQueXDX(xdx2);
                    }
                    x2s = new XDX2SDS(xdx2.x, xdx2.dxu, RADControl.errorLimit);
                    ((Alpha)d).setAID(xdx2.x);
                    ((Alpha)d).setDAID(xdx2.dxu);
                    ((Alpha)d).setAIS(x2s.s());
                    ((Alpha)d).setDAIS(x2s.ds());
                    if (isSuppressed || xdx2.x < (double)RADControl.MIN_RI && RADControl.omitWeakRadiation) {
                        this.omittedDecayE = this.omittedDecayE.add(xdx2.multiply(((Alpha)d).EF(), ((Alpha)d).DEF()));
                        this.omittedDecayI = this.omittedDecayI.add(xdx2);
                        this.omittedDecaysV.add(d);
                        if (isSuppressed) {
                            this.suppressedDecaysV.add(d);
                        }
                    } else {
                        this.totalDecayE = this.totalDecayE.add(xdx2.multiply(((Alpha)d).EF(), ((Alpha)d).DEF()));
                        this.totalDecayI = this.totalDecayI.add(xdx2);
                    }
                }
                ++i5;
            }
        }
        XDX recoilFactor = this.ONE().subtract(this.massRatio, true);
        if (this.massRatio != null && this.massRatio.x != 0.0) {
            recoilFactor = recoilFactor.divided(this.massRatio, true);
        }
        this.totalRecoilE = this.isDelayed ? this.totalRecoilE.add(this.totalDelayE.multiply(recoilFactor)) : this.totalRecoilE.add(this.totalDecayE.multiply(recoilFactor));
        XDX wk = this.atomic.wk;
        XDX wl = this.atomic.wl;
        XDX ieKAuger = ieK.multiply(this.ONE().subtract(wk, true));
        if (ieKAuger.x > 0.0 && this.AugerMap.containsKey("K")) {
            rad = this.AugerMap.get("K");
            rad.setIntensity(ieKAuger.x, ieKAuger.dxu);
            if (ieKAuger.x < (double)RADControl.MIN_RI && RADControl.omitWeakRadiation) {
                this.omittedCEAugerE = this.omittedCEAugerE.add(this.atomic.aeK.multiply(ieKAuger));
                this.omittedCEAugerI = this.omittedCEAugerI.add(ieKAuger);
                this.omittedAugersV.add(rad);
            } else {
                this.totalAugerE = this.totalAugerE.add(this.atomic.aeK.multiply(ieKAuger));
                this.totalAugerI = this.totalAugerI.add(ieKAuger);
            }
        }
        XDX ieLAuger = ieL.add(ieK.multiply(this.atomic.prob, true));
        ieLAuger = ieLAuger.multiply(this.ONE().subtract(wl, true));
        if (ieLAuger.x > 0.0 && this.AugerMap.containsKey("L")) {
            rad = this.AugerMap.get("L");
            rad.setIntensity(ieLAuger.x, ieLAuger.dxu);
            if (ieLAuger.x < (double)RADControl.MIN_RI && RADControl.omitWeakRadiation) {
                this.omittedCEAugerE = this.omittedCEAugerE.add(this.atomic.aeL.multiply(ieLAuger));
                this.omittedCEAugerI = this.omittedCEAugerI.add(ieLAuger);
                this.omittedAugersV.add(rad);
            } else {
                this.totalAugerE = this.totalAugerE.add(this.atomic.aeL.multiply(ieLAuger));
                this.totalAugerI = this.totalAugerI.add(ieLAuger);
            }
        }
        XDX ixK = ieK.multiply(wk, true);
        XDX ixKA = ixK.divided(this.ONE().add(this.atomic.ratioKBKA, true));
        XDX tempF = this.ONE().divided(this.ONE().add(this.atomic.ratioKBKA, true));
        tempF = this.ONE().subtract(tempF);
        XDX ixKB = ixK.multiply(tempF);
        XDX ixKA1 = ixKA.divided(this.ONE().add(this.atomic.ratioKA2KA1, true));
        tempF = this.ONE().divided(this.ONE().add(this.atomic.ratioKA2KA1, true));
        tempF = this.ONE().subtract(tempF);
        XDX ixKA2 = ixKA.multiply(tempF);
        if (ixKA1.x > 0.0 && this.xrayMap.containsKey("KA1")) {
            rad = this.xrayMap.get("KA1");
            rad.setIntensity(ixKA1.x, ixKA1.dxu);
            if (ixKA1.x < (double)RADControl.MIN_RI && RADControl.omitWeakRadiation) {
                this.omittedPhotonE = this.omittedPhotonE.add(ixKA1.multiply(rad.EF()));
                this.omittedPhotonI = this.omittedPhotonI.add(ixKA1);
                this.omittedXraysV.add(rad);
            } else {
                this.totalXrayE = this.totalXrayE.add(ixKA1.multiply(rad.EF()));
                this.totalXrayI = this.totalXrayI.add(ixKA1);
            }
        }
        if (ixKA2.x > 0.0 && this.xrayMap.containsKey("KA2")) {
            rad = this.xrayMap.get("KA2");
            rad.setIntensity(ixKA2.x, ixKA2.dxu);
            if (ixKA2.x < (double)RADControl.MIN_RI && RADControl.omitWeakRadiation) {
                this.omittedPhotonE = this.omittedPhotonE.add(ixKA2.multiply(rad.EF()));
                this.omittedPhotonI = this.omittedPhotonI.add(ixKA2);
                this.omittedXraysV.add(rad);
            } else {
                this.totalXrayE = this.totalXrayE.add(ixKA2.multiply(rad.EF()));
                this.totalXrayI = this.totalXrayI.add(ixKA2);
            }
        }
        if (ixKB.x > 0.0 && this.xrayMap.containsKey("KB")) {
            rad = this.xrayMap.get("KB");
            rad.setIntensity(ixKB.x, ixKB.dxu);
            if (ixKB.x < (double)RADControl.MIN_RI && RADControl.omitWeakRadiation) {
                this.omittedPhotonE = this.omittedPhotonE.add(ixKB.multiply(rad.EF()));
                this.omittedPhotonI = this.omittedPhotonI.add(ixKB);
                this.omittedXraysV.add(rad);
            } else {
                this.totalXrayE = this.totalXrayE.add(ixKB.multiply(rad.EF()));
                this.totalXrayI = this.totalXrayI.add(ixKB);
            }
        }
        XDX ixL = ieL.add(ieK.multiply(this.atomic.prob, true));
        ixL = ixL.multiply(wl, true);
        if (ixL.x > 0.0 && this.xrayMap.containsKey("L")) {
            rad = this.xrayMap.get("L");
            rad.setIntensity(ixL.x, ixL.dxu);
            if (ixL.x < (double)RADControl.MIN_RI && RADControl.omitWeakRadiation) {
                this.omittedPhotonE = this.omittedPhotonE.add(ixL.multiply(rad.EF()));
                this.omittedPhotonI = this.omittedPhotonI.add(ixL);
                this.omittedXraysV.add(rad);
            } else {
                this.totalXrayE = this.totalXrayE.add(ixL.multiply(rad.EF()));
                this.totalXrayI = this.totalXrayI.add(ixL);
            }
        }
        if (nGoodBetas == 1) {
            Decay d3 = (Decay)this.betasV.get(0);
            XDX eav = this.EAV.get(d3);
            if (eav.x > 0.0) {
                this.totalAvgBetaE = new XDX(eav.x, eav.dxu);
            }
        } else if (this.totalBetaI.x > 0.0) {
            float diff = 0.0f;
            float ti = (float)this.totalBetaI.x;
            float te = (float)this.totalBetaE.x;
            i = 0;
            while (i < this.betasV.size()) {
                Decay d4 = (Decay)this.betasV.get(i);
                XDX eav = this.EAV.get(d4);
                float e = (float)eav.x;
                float de = (float)eav.dxu;
                float ai = (float)d4.AID();
                float dai = (float)d4.DAID();
                if (!(e <= 0.0f) && !(dai <= 0.0f)) {
                    diff = (float)((double)diff + (double)(te * (2.0f * e * ti - te) * dai * dai) / Math.pow(ti, 4.0));
                }
                ++i;
            }
            this.totalAvgBetaE = this.totalBetaE.divided(this.totalBetaI.x);
            this.totalAvgBetaE.dxl = this.totalAvgBetaE.dxu = Math.sqrt(this.totalAvgBetaE.dxu * this.totalAvgBetaE.dxu - (double)diff);
        }
        this.totalPhotonE = this.totalPhotonE.add(this.totalGammaE);
        this.totalPhotonI = this.totalPhotonI.add(this.totalGammaI);
        this.totalPhotonE = this.totalPhotonE.add(this.totalXrayE);
        this.totalPhotonI = this.totalPhotonI.add(this.totalXrayI);
        this.totalPhotonE = this.totalPhotonE.add(this.annihilationE);
        this.totalPhotonI = this.totalPhotonI.add(this.annihilationI);
        XDX[] allEs = new XDX[]{this.totalDecayE, this.totalAugerE, this.totalCeE, this.totalPhotonE, this.totalRecoilE, this.totalDelayE};
        XDX[] allIs = new XDX[]{this.totalDecayI, this.totalAugerI, this.totalCeI, this.totalPhotonI, this.totalDelayI};
        int i6 = 0;
        while (i6 < allEs.length) {
            this.totalE = this.totalE.add(allEs[i6]);
            ++i6;
        }
        this.totalAbsorbedE = this.totalBetaE.add(this.totalAugerE).add(this.totalCeE).add(this.totalPhotonE).add(this.totalRecoilE).add(this.totalDelayE);
        this.allOmittedE = this.omittedDecayE.add(this.omittedCEAugerE).add(this.omittedPhotonE);
        i6 = 0;
        while (i6 < allIs.length) {
            this.totalI = this.totalI.add(allIs[i6]);
            ++i6;
        }
        Vector<XDX> allEsV = this.getEnergiesV();
        i = 0;
        while (i < allEsV.size()) {
            allEsV.get(i).scale(0.01);
            ++i;
        }
    }

    public void print() {
        System.out.println(" DECAY      =" + this.totalDecayE.x + " " + this.totalDecayE.dxu);
        System.out.println(" BETA       =" + this.totalBetaE.x + " " + this.totalBetaE.dxu);
        System.out.println(" CE+Auger   =" + this.totalCeE.x + " " + this.totalCeE.dxu);
        System.out.println(" PHOTON     =" + this.totalPhotonE.x + " " + this.totalPhotonE.dxu);
        System.out.println(" UNPL/GAM   =" + this.totalUnpGammaE.x + " " + this.totalUnpGammaE.dxu);
        System.out.println(" RECOIL     =" + this.totalRecoilE.x + " " + this.totalRecoilE.dxu);
        System.out.println(" Neutrino   =" + this.totalNeutrinoE.x + " " + this.totalNeutrinoE.dxu);
        System.out.println(" Absorbed   =" + this.totalAbsorbedE.x + " " + this.totalAbsorbedE.dxu);
        System.out.println(" Annihilation=" + this.annihilationE.x + " " + this.annihilationE.dxu);
        System.out.println(" TOTAL      =" + this.totalE.x + " " + this.totalE.dxu);
    }

    /*
     * Unable to fully structure code
     */
    public static float[] findGridEnergy(float minE, float maxE, float initialStep, int MAX_NGRID, int stepMultiplier) {
        out = new float[]{};
        if (maxE <= 0.0f || minE > maxE || initialStep <= 0.0f || stepMultiplier <= 0) {
            return out;
        }
        if (minE < 0.0f) {
            minE = 0.0f;
        }
        E = new float[MAX_NGRID];
        Arrays.fill(E, 0.0f);
        E[0] = minE;
        n1 = (int)(minE / initialStep);
        diff = maxE - minE;
        tempStep = initialStep;
        nBins = 0;
        step0 = initialStep;
        multiplier0 = stepMultiplier;
        found = false;
        while (!found) {
            block43: {
                tempStep = initialStep;
                while (diff / ((float)stepMultiplier * tempStep) > 4.0f && stepMultiplier > 1) {
                    tempStep = (float)stepMultiplier * tempStep;
                }
                nBins = (int)(diff / ((float)stepMultiplier * tempStep));
                if (nBins == 0) break;
                nb = nBins;
                st = tempStep;
                m = stepMultiplier;
                if (nBins <= MAX_NGRID) break;
                nb = nBins;
                st = tempStep;
                m = stepMultiplier * 2;
                if (m >= 10) ** GOTO lbl44
                while (m < 10) {
                    nb = (int)(diff / ((float)m * st));
                    if (nb <= MAX_NGRID) {
                        found = true;
                        break block43;
                    }
                    m *= 2;
                }
                break block43;
lbl-1000:
                // 1 sources

                {
                    nb = (int)(diff / ((float)m * st));
                    if (nb <= MAX_NGRID) {
                        found = true;
                        break;
                    }
                    st *= (float)m;
lbl44:
                    // 2 sources

                    ** while (st < 20.0f)
                }
            }
            if (!found) {
                toBreak = false;
                st = tempStep;
                m = stepMultiplier * 2;
                while (true) {
                    if ((nb = (int)(diff / ((float)m * st))) == 0) {
                        toBreak = true;
                    } else if (nb <= MAX_NGRID) {
                        found = true;
                    } else {
                        st *= (float)m;
                        continue;
                    }
                    if (found || toBreak) break;
                    m *= 2;
                }
            }
            if (!found) {
                toBreak = false;
                st = tempStep;
                m = stepMultiplier * 2;
                while (true) {
                    if ((nb = (int)(diff / ((float)m * st))) == 0) {
                        toBreak = true;
                    } else if (nb <= MAX_NGRID) {
                        found = true;
                    } else {
                        m *= 2;
                        continue;
                    }
                    if (found || toBreak) break;
                    st *= (float)m;
                }
            }
            nBins = nb;
            stepMultiplier = m;
            initialStep = st;
        }
        if (initialStep != step0 && stepMultiplier != multiplier0) {
            System.out.println("*** intital bin size has been adjusted from " + step0 + " to " + initialStep);
            System.out.println("*** bin-size multiplier has been adjusted from " + multiplier0 + " to " + stepMultiplier);
        } else if (initialStep != step0) {
            System.out.println("*** intital bin size has been adjusted from " + step0 + " to " + initialStep);
        } else if (stepMultiplier != multiplier0) {
            System.out.println("*** bin-size multiplier has been adjusted from " + multiplier0 + " to " + stepMultiplier);
        }
        offset0 = 0.0f;
        startE = minE;
        if (n1 <= 4) {
            nBins += 5 - n1;
        }
        if (n1 > 1 && nBins < 5) {
            offset0 = (float)(n1 / 2 * 2) * initialStep;
            if (offset0 > 10.0f) {
                offset0 = (int)(offset0 / (float)stepMultiplier) * stepMultiplier;
            }
            startE = minE - offset0;
        }
        n = 1;
        prevStep = initStep = initialStep;
        step = initStep;
        retry = false;
        done = false;
        while (true) {
            retry = false;
            start = (int)(startE / step);
            offset = 0.0f;
            E0 = (float)(start + 1) * step + offset0;
            if (n > 0 && E0 <= E[n - 1]) {
                offset = E[n - 1];
            }
            i = start;
            while (i < 5) {
                E[n] = (float)(i + 1) * step + offset0 + offset;
                if (!(E[n] >= maxE)) ** GOTO lbl142
                if ((double)Math.abs(E[n] - maxE) <= 0.5 * (double)step && n >= 2) {
                    n -= 2;
                    j = 0;
                    while (j < 100) {
                        E[n + 1] = E[n] + prevStep;
                        if (E[n + 1] >= maxE) {
                            if (n >= MAX_NGRID - 1) {
                                initStep = (float)stepMultiplier * initStep;
                                retry = true;
                                break;
                            }
                            E[n + 1] = maxE;
                            ++n;
                            done = true;
                            break;
                        }
                        if (++n > MAX_NGRID - 2) {
                            initStep = (float)stepMultiplier * initStep;
                            retry = true;
                            break;
                        }
                        ++j;
                    }
                    if (retry || done) {
                        break;
                    }
                } else {
                    E[n] = maxE;
                    done = true;
                    break;
lbl142:
                    // 1 sources

                    if (++n > MAX_NGRID - 1) {
                        initStep = (float)stepMultiplier * initStep;
                        retry = true;
                        break;
                    }
                }
                ++i;
            }
            if (retry) {
                n = 1;
                Arrays.fill(E, 0.0f);
                prevStep = initStep;
                step = initStep;
                continue;
            }
            prevStep = step;
            step = (float)stepMultiplier * step;
            if (done) break;
        }
        if (done) {
            out = new float[n + 1];
            i = 0;
            while (i <= n) {
                out[i] = E[i];
                ++i;
            }
        } else {
            out = new float[]{};
        }
        return out;
    }

    public float[] findGridEnergy(float initialStep, int step_multiplier) {
        float maxE = 0.0f;
        float maxDE = 0.0f;
        int i = 0;
        while (i < this.decaysV.size()) {
            Decay decay = (Decay)this.decaysV.get(i);
            if (!this.isFakeDecay(decay)) {
                float e = decay.EF();
                float de = decay.DEF();
                if (e > maxE) {
                    maxE = e;
                    maxDE = de;
                }
            }
            ++i;
        }
        if (maxDE > 0.0f) {
            maxE += maxDE;
        }
        return DecayDataset.findGridEnergy(0.0f, maxE, initialStep, RADControl.MAX_NGRID, step_multiplier);
    }

    public void setGridEnergyForIB(float[] gridE) {
        this.gridEIB = (float[])gridE.clone();
    }

    public void setGridEnergyForBeta(float[] gridE) {
        this.gridEB = (float[])gridE.clone();
    }

    public void calculateContinua() {
        String betaType = "B-";
        if (this.decayType.equals("EC")) {
            betaType = "B+";
        }
        String tempMsg = "";
        StringWriter.reset();
        this.spectraV.clear();
        int i = 0;
        while (i < this.decaysV.size()) {
            Decay d = (Decay)this.decaysV.get(i);
            boolean hasFeeding = true;
            if (d.AID() <= 0.0 && d.ITS().isEmpty() && d.IES().isEmpty()) {
                hasFeeding = false;
            }
            if (this.isFakeDecay(d)) {
                this.spectraV.add(null);
            } else {
                DecaySpectrum spec = new DecaySpectrum(this.Z, this.A, d.EF(), d.DEF(), this.decayType, this.parent, d);
                this.spectraV.add(spec);
                spec.setHasInputFeeding(hasFeeding);
                spec.calculate(this.gridEB, this.gridEIB, RADControl.calculateBremsstrahlung);
                this.message = String.valueOf(this.message) + spec.getMessage();
                if (hasFeeding) {
                    double de = d.DEAvF();
                    if (de <= 0.0) {
                        de = 0.5;
                    }
                    double avgE = spec.averageEnergy().x;
                    double davgE = spec.averageEnergy().dxu;
                    if (davgE <= 0.0) {
                        davgE = avgE * 0.5;
                    }
                    de = Math.sqrt(de * de + davgE * davgE);
                    if (d.EAvS().length() > 0 && Math.abs(avgE - (double)d.EAvF()) > de && d.AID() > 0.0) {
                        String FLS = d.getFLS();
                        String ECALS = Util.printXDX(avgE, davgE);
                        String EAVS = Util.printSDS(d.EAvS(), d.DEAvS());
                        StringWriter.write("***Warning: at level=%10s, calculated <E%2s>=%s disagrees with %s in file\n", FLS, betaType, ECALS, EAVS);
                    }
                }
            }
            ++i;
        }
        tempMsg = StringWriter.squeeze();
        if (tempMsg.length() > 0) {
            this.message = String.valueOf(this.message) + tempMsg;
        }
    }

    public XDX branching() {
        return this.BR;
    }

    public XDX DParticleBR() {
        return this.dpBR;
    }

    public XDX dpBranching() {
        return this.dpBR;
    }

    public XDX QValue() {
        return this.Q;
    }

    public boolean isValid() {
        return this.isValid;
    }

    public boolean hasParent() {
        return this.hasParent;
    }

    public boolean hasNorm() {
        return this.hasNorm;
    }

    public String decayType() {
        return this.decayType;
    }

    public String betaType() {
        return this.betaType;
    }

    public ENSDF getENSDF() {
        return this.ens;
    }

    public int nSpectra() {
        return this.spectraV.size();
    }

    public Vector<DecaySpectrum> getSpectra() {
        return this.spectraV;
    }

    public DecaySpectrum spectrumAt(int n) {
        try {
            return this.spectraV.get(n);
        }
        catch (Exception exception) {
            return null;
        }
    }

    public Vector<Decay> decaysV() {
        return this.decaysV;
    }

    public int nDecays() {
        return this.decaysV.size();
    }

    public int nDelays() {
        return this.dpsV.size();
    }

    public String getMessage() {
        return this.message;
    }

    public void clearMessage() {
        this.message = "";
    }

    static interface AbsoluteRI {
        public XDX get(double var1, double var3, String var5, String var6, char var7);
    }

    static interface GetDecayEnergy {
        public XDX2SDS get(ENSDF var1, Level var2);
    }

    static interface GetEAV {
        public XDX get(Decay var1);
    }

    static interface UncParser {
        public XDX get(String var1, String var2);
    }
}
