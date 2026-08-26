/*
 * Decompiled with CFR 0.152.
 */
package ensdfparser.ensdf;

import ensdfparser.ensdf.ContRecord;
import ensdfparser.ensdf.Decay;
import ensdfparser.ensdf.FindValue;
import ensdfparser.ensdf.Nucleus;
import ensdfparser.ensdf.SDS2XDX;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

public class Beta
extends Decay {
    protected String level_s;
    protected int level_i;
    protected String flevel_s;
    protected String flevel_jpis;
    protected int flevel_i;
    protected boolean isPlaced;
    protected final String decayType = "B";
    protected String e_s;
    protected String e_s0;
    protected String de_s;
    protected String de_s0;
    protected float e_f;
    protected float de_f;
    protected String recoilE_s;
    protected String drecoilE_s;
    protected String eav_s;
    protected String deav_s;
    protected float eav_f;
    protected float deav_f;
    protected String epe_s;
    protected String depe_s;
    protected float epe_f;
    protected float depe_f;
    protected String i_s;
    protected String di_s;
    protected double i_d;
    protected double di_d;
    protected double i_sy_d;
    protected double di_sy_d;
    protected String ai_s;
    protected String dai_s;
    protected double ai_d;
    protected double dai_d;
    protected double daill_d;
    protected String dose_s;
    protected String ddose_s;
    protected float dose_f;
    protected float ddose_f;
    protected String logft_s;
    protected String dlogft_s;
    protected String flag_s;
    protected String coin_s;
    protected String u_s;
    protected String q_s;
    protected int order;
    protected String dpType = "";
    protected String enu_s;
    protected String denu_s;
    protected float enu_f;
    protected float denu_f;

    public Beta() {
        this.lines.clear();
        this.commentV.clear();
        this.crLineV.clear();
        this.crV.clear();
        this.e_s = "";
        this.de_s = "";
        this.e_f = -1.0f;
        this.de_f = -1.0f;
        this.e_s0 = "";
        this.de_s0 = "";
        this.eav_s = "";
        this.deav_s = "";
        this.eav_f = -1.0f;
        this.deav_f = -1.0f;
        this.epe_s = "";
        this.depe_s = "";
        this.epe_f = -1.0f;
        this.depe_f = -1.0f;
        this.i_s = "";
        this.di_s = "";
        this.i_d = -1.0;
        this.di_d = -1.0;
        this.i_sy_d = -1.0;
        this.di_sy_d = -1.0;
        this.ai_s = "";
        this.dai_s = "";
        this.ai_d = -1.0;
        this.dai_d = -1.0;
        this.daill_d = -1.0;
        this.logft_s = "";
        this.dlogft_s = "";
        this.flag_s = "";
        this.coin_s = "";
        this.u_s = "";
        this.q_s = "";
        this.dose_s = "";
        this.ddose_s = "";
        this.dose_f = -1.0f;
        this.ddose_f = -1.0f;
        this.dpType = "";
        this.order = -1;
        this.isPlaced = true;
    }

    public void setValues(Vector<String> v) throws Exception {
        this.lines.clear();
        this.lines.addAll(v);
        if (this.lines.size() > 0) {
            this.doWork();
        }
    }

    protected void parseEB() {
        SDS2XDX e = new SDS2XDX();
        e.setValues(this.e_s, this.de_s);
        this.e_f = (float)e.X();
        this.de_f = (float)e.DX();
    }

    protected void parseIB() {
        if (this.i_s.length() > 0) {
            SDS2XDX i = new SDS2XDX();
            i.setValues(this.i_s, this.di_s);
            this.i_d = i.X();
            this.di_d = i.DX();
            this.i_sy_d = i.XS();
            this.di_sy_d = i.DXS();
        } else {
            this.i_d = -1.0;
            this.di_d = -1.0;
            this.i_sy_d = -1.0;
            this.di_sy_d = -1.0;
        }
    }

    private void doWork() throws Exception {
        try {
            this.recordLine = (String)this.lines.elementAt(0);
            String nucName = ((String)this.lines.elementAt(0)).substring(0, 5).trim();
            this.nuc = new Nucleus();
            this.nuc.setGen(nucName);
            this.e_s = ((String)this.lines.elementAt(0)).substring(9, 19).trim();
            this.de_s = ((String)this.lines.elementAt(0)).substring(19, 21).trim();
            this.i_s = ((String)this.lines.elementAt(0)).substring(21, 29).trim();
            this.di_s = ((String)this.lines.elementAt(0)).substring(29, 31).trim();
            this.e_s0 = this.e_s;
            this.de_s0 = this.de_s;
            this.logft_s = ((String)this.lines.elementAt(0)).substring(41, 49).trim();
            this.dlogft_s = ((String)this.lines.elementAt(0)).substring(49, 55).trim();
            this.flag_s = ((String)this.lines.elementAt(0)).substring(76, 77).trim();
            if (this.flag_s.equals("C") || this.flag_s.equals("?")) {
                this.coin_s = this.flag_s;
                this.flag_s = "";
            }
            this.u_s = ((String)this.lines.elementAt(0)).substring(77, 79).trim();
            this.q_s = ((String)this.lines.elementAt(0)).substring(79).trim();
        }
        catch (Exception nucName) {
            // empty catch block
        }
        this.parseEB();
        this.parseIB();
        this.parseComments("B");
        boolean isNewCRLine = false;
        boolean isExtension = false;
        ArrayList<String> recordNames = new ArrayList<String>(Arrays.asList("E", "IB", "LOGFT"));
        String line = "";
        int k = 1;
        while (k < this.lines.size()) {
            line = (String)this.lines.elementAt(k);
            if (line.substring(6, 9).equals(" B ") && !line.substring(5, 6).equals(" ")) {
                isNewCRLine = true;
                FindValue fv = new FindValue();
                this.crLineV.addElement(line);
                Vector<String> entries = this.findContRecordEntries(line);
                boolean isSuppressed = line.substring(5, 6).equals("S");
                boolean skip = false;
                String contType = line.substring(5, 6);
                int m = 0;
                while (m < entries.size()) {
                    String entry = entries.get(m);
                    fv.clear();
                    fv.findValueInEntry(entry);
                    String name = fv.name();
                    if (fv.canGetV()) {
                        ContRecord cr = new ContRecord();
                        String crName = name;
                        String crText = fv.txt();
                        skip = false;
                        isExtension = false;
                        boolean isSupTemp = isSuppressed;
                        if (recordNames.contains(crName)) {
                            isSupTemp = false;
                            isExtension = true;
                            skip = true;
                        }
                        cr.setValues(crName, fv.symbol(), fv.s(), fv.units(), fv.ds(), fv.ref(), crText, contType, isSupTemp, isNewCRLine, isExtension);
                        this.crV.add(cr);
                        if (name.equals("FLAG")) {
                            this.flag_s = String.valueOf(this.flag_s) + fv.v();
                            this.crV.remove(cr);
                            skip = true;
                        } else if (name.equals("EAV")) {
                            this.eav_s = fv.s();
                            this.deav_s = fv.ds();
                            this.eav_f = fv.xf();
                            this.deav_f = fv.dxf();
                            isNewCRLine = false;
                        } else if (name.equals("E") && this.e_s.isEmpty()) {
                            this.e_s = cr.s();
                            this.de_s = cr.ds();
                            this.e_s0 = this.e_s;
                            this.de_s0 = this.de_s;
                            this.parseEB();
                        } else if (name.equals("IB") && this.i_s.isEmpty()) {
                            this.i_s = cr.s();
                            this.di_s = cr.ds();
                            this.parseIB();
                        } else if (name.equals("LOGFT") && this.logft_s.isEmpty()) {
                            this.logft_s = cr.s();
                            this.dlogft_s = cr.ds();
                        }
                        if (!skip) {
                            isNewCRLine = false;
                        }
                    }
                    ++m;
                }
            }
            ++k;
        }
        this.makeContRecordsVMap();
        this.lines.clear();
    }

    protected String rTrim(String s) {
        String st = s.trim();
        int i = s.indexOf(st);
        String srt = String.valueOf(s.substring(0, i)) + st;
        return srt;
    }

    @Override
    public String ES0() {
        return this.e_s0.toLowerCase().replace("e", "E").replace("s(", "S(");
    }

    @Override
    public String DES0() {
        return this.de_s0;
    }

    @Override
    public String ES() {
        return this.e_s.toLowerCase().replace("e", "E").replace("s(", "S(");
    }

    @Override
    public String DES() {
        return this.de_s;
    }

    @Override
    public float EF() {
        return this.e_f;
    }

    @Override
    public float DEF() {
        return this.de_f;
    }

    @Override
    public String EAvS() {
        return this.eav_s;
    }

    @Override
    public String DEAvS() {
        return this.deav_s;
    }

    @Override
    public float EAvF() {
        return this.eav_f;
    }

    @Override
    public float DEAvF() {
        return this.deav_f;
    }

    @Override
    public void setEAvF(float f) {
        this.eav_f = f;
    }

    @Override
    public void setDEAvF(float f) {
        this.deav_f = f;
    }

    @Override
    public void setES(String ES) {
        this.e_s = ES;
    }

    @Override
    public void setDES(String DES) {
        this.de_s = DES;
    }

    @Override
    public void setEF(float ef) {
        this.e_f = ef;
    }

    @Override
    public void setDEF(float def) {
        this.de_f = def;
    }

    public void setEPES(String s) {
        this.epe_s = s;
    }

    public void setDEPES(String s) {
        this.depe_s = s;
    }

    public String EPES() {
        return this.epe_s;
    }

    public String DEPES() {
        return this.depe_s;
    }

    public void setEPEF(float f) {
        this.epe_f = f;
    }

    public void setDEPEF(float f) {
        this.depe_f = f;
    }

    public float EPEF() {
        return this.epe_f;
    }

    public float DEPEF() {
        return this.depe_f;
    }

    @Override
    public String RIS() {
        return this.i_s;
    }

    @Override
    public String DRIS() {
        return this.di_s;
    }

    @Override
    public double ID() {
        return this.i_d;
    }

    @Override
    public double DID() {
        return this.di_d;
    }

    public double syID() {
        return this.i_sy_d;
    }

    public double DsyID() {
        return this.di_sy_d;
    }

    public void setsyID(double d) {
        this.i_sy_d = d;
    }

    public void setDsyID(double d) {
        this.di_sy_d = d;
    }

    @Override
    public void setAIS(String s) {
        this.ai_s = s;
    }

    @Override
    public void setDAIS(String s) {
        this.dai_s = s;
    }

    @Override
    public String AIS() {
        return this.ai_s;
    }

    @Override
    public String DAIS() {
        return this.dai_s;
    }

    public String AIUnicode() {
        String aiu_s = "";
        if (this.ai_s.length() > 0) {
            aiu_s = this.di_s.equals("GT") ? String.valueOf(aiu_s) + ">" + this.ai_s : (this.di_s.equals("GE") ? String.valueOf(aiu_s) + '\u2265' + this.ai_s : (this.di_s.equals("LT") ? String.valueOf(aiu_s) + ">" + this.ai_s : (this.di_s.equals("LE") ? String.valueOf(aiu_s) + '\u2264' + this.ai_s : (this.di_s.equals("AP") ? String.valueOf(aiu_s) + '\u2248' + this.ai_s : this.ai_s))));
        }
        return aiu_s;
    }

    @Override
    public void setAID(double d) {
        this.ai_d = d;
    }

    @Override
    public void setDAID(double d) {
        this.dai_d = d;
    }

    @Override
    public double AID() {
        return this.ai_d;
    }

    @Override
    public double DAID() {
        return this.dai_d;
    }

    public void setDAILLD(double d) {
        this.daill_d = d;
    }

    public double DAILLD() {
        return this.daill_d;
    }

    @Override
    public void setDoseS(String s) {
        this.dose_s = s;
    }

    @Override
    public void setDDoseS(String s) {
        this.ddose_s = s;
    }

    @Override
    public String DoseS() {
        return this.dose_s;
    }

    @Override
    public String DDoseS() {
        return this.ddose_s;
    }

    @Override
    public void setDoseF(float f) {
        this.dose_f = f;
    }

    @Override
    public void setDDoseF(float f) {
        this.ddose_f = f;
    }

    @Override
    public float DoseF() {
        return this.dose_f;
    }

    @Override
    public float DDoseF() {
        return this.ddose_f;
    }

    @Override
    public String LOGFTS() {
        return this.logft_s;
    }

    @Override
    public String DLOGFTS() {
        return this.dlogft_s;
    }

    public String LogftUnicode() {
        String logftu_s = "";
        if (this.logft_s.length() > 0) {
            logftu_s = this.dlogft_s.equals("GT") ? ">" + this.logft_s : (this.dlogft_s.equals("GE") ? String.valueOf('\u2265') + this.logft_s : (this.dlogft_s.equals("LT") ? ">" + this.logft_s : (this.dlogft_s.equals("LE") ? String.valueOf('\u2264') + this.logft_s : (this.dlogft_s.equals("AP") ? String.valueOf('\u2248') + this.logft_s : this.logft_s))));
            logftu_s = String.valueOf(logftu_s) + " ";
        }
        return logftu_s;
    }

    @Override
    public String flagS() {
        return this.flag_s;
    }

    @Override
    public void addFlag(String s) {
        this.flag_s = String.valueOf(this.flag_s) + s;
    }

    @Override
    public String flag() {
        return this.flag_s;
    }

    @Override
    public String coinS() {
        return this.coin_s;
    }

    @Override
    public String QS() {
        return this.q_s;
    }

    @Override
    public String q() {
        return this.q_s;
    }

    @Override
    public String US() {
        return this.u_s;
    }

    @Override
    public String unique() {
        return this.u_s;
    }

    @Override
    public String recoilES() {
        return this.recoilE_s;
    }

    @Override
    public String recoilDES() {
        return this.drecoilE_s;
    }

    public void setOrder(int i) {
        this.order = i;
    }

    public int getOrder() {
        return this.order;
    }

    public void setUnplaced() {
        this.isPlaced = false;
    }

    public boolean isPlaced() {
        return this.isPlaced;
    }

    public void setENeutrino(String s, String ds, float f, float df) {
        this.enu_s = s;
        this.denu_s = ds;
        this.enu_f = f;
        this.denu_f = df;
    }

    public String ENuS() {
        return this.enu_s;
    }

    public String DENuS() {
        return this.denu_s;
    }

    public float ENuF() {
        return this.enu_f;
    }

    public float DENuF() {
        return this.denu_f;
    }

    public void isBlank() {
        this.e_s = "new Beta";
    }

    @Override
    public void setFLI(int FLI) {
        this.flevel_i = FLI;
    }

    @Override
    public void setFLS(String FLS) {
        this.flevel_s = FLS;
    }

    @Override
    public void setJFS(String JFS) {
        this.flevel_jpis = JFS;
    }

    @Override
    public int getFLI() {
        return this.flevel_i;
    }

    @Override
    public String getFLS() {
        return this.flevel_s;
    }

    @Override
    public String getJFS() {
        return this.flevel_jpis;
    }

    public int getLevelIndex() {
        return this.level_i;
    }

    @Override
    public void setRecoilES(String ers) {
        this.recoilE_s = ers;
    }

    @Override
    public void setRecoilDES(String ders) {
        this.drecoilE_s = ders;
    }

    @Override
    public String IES() {
        return "";
    }

    @Override
    public String DIES() {
        return "";
    }

    @Override
    public String ITS() {
        return "";
    }

    @Override
    public String DITS() {
        return "";
    }

    @Override
    public String HFS() {
        return "";
    }

    @Override
    public String DHFS() {
        return "";
    }

    @Override
    public String decayType() {
        return "B";
    }

    @Override
    public String dpType() {
        return this.dpType;
    }

    @Override
    public void setDPType(String type) {
        this.dpType = type;
    }

    @Override
    public void setEAvS(String s) {
        this.eav_s = s;
    }

    @Override
    public void setDEAvS(String s) {
        this.deav_s = s;
    }

    @Override
    public void setRecoilDoseS(String s) {
    }

    @Override
    public void setRecoilDDoseS(String s) {
    }

    @Override
    public String recoilDoseS() {
        return "";
    }

    @Override
    public String recoilDDoseS() {
        return "";
    }

    @Override
    public void setRecoilDoseF(float f) {
    }

    @Override
    public void setRecoilDDoseF(float f) {
    }

    @Override
    public float recoilDoseF() {
        return 0.0f;
    }

    @Override
    public float recoilDDoseF() {
        return 0.0f;
    }
}
