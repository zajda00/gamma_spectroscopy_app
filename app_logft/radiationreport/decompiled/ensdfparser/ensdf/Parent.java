/*
 * Decompiled with CFR 0.152.
 */
package ensdfparser.ensdf;

import ensdfparser.ensdf.Comment;
import ensdfparser.ensdf.ContRecord;
import ensdfparser.ensdf.DecayMode;
import ensdfparser.ensdf.FindValue;
import ensdfparser.ensdf.Level;
import ensdfparser.ensdf.Nucleus;
import ensdfparser.ensdf.Record;
import ensdfparser.ensdf.SDS2XDX;
import ensdfparser.nds.util.Str;
import java.util.Vector;

public class Parent
extends Record {
    protected String dm_name_s;
    protected Nucleus nuc = new Nucleus();
    protected Level lev = new Level();
    protected String q_s;
    protected String dq_s;
    protected float q_f;
    protected float dq_f;
    protected String ion_s;
    protected String qMev_s;
    protected DecayMode dm = new DecayMode();
    protected String indexS = "";

    public Parent() {
        this.lines.clear();
        this.commentV.clear();
    }

    public void setValues(Vector<String> v) throws Exception {
        this.lines.clear();
        this.lines.addAll(v);
        if (this.lines.size() > 0) {
            this.doWork();
        }
    }

    public void doWork() throws Exception {
        this.recordLine = (String)this.lines.elementAt(0);
        this.q_s = ((String)this.lines.elementAt(0)).substring(64, 74).trim();
        this.dq_s = ((String)this.lines.elementAt(0)).substring(74, 76).trim();
        String scrap = "                                               ";
        String line = "";
        Vector<String> vjunk = new Vector<String>();
        vjunk.addElement(String.valueOf(((String)this.lines.elementAt(0)).substring(0, 56)) + scrap);
        String qEntry = "";
        int k = 1;
        while (k < this.lines.size()) {
            line = Str.fixLineLength((String)this.lines.elementAt(k), 80);
            String contType = line.substring(5, 6);
            qEntry = "";
            if (line.substring(6, 8).equals(" P") && !line.substring(5, 6).equals(" ")) {
                String s = "";
                int n1 = line.indexOf("QP");
                if (n1 > 0) {
                    int n2 = line.indexOf("$", n1);
                    s = n2 > 0 ? line.substring(n1, n2 + 1) : line.substring(n1);
                    line = line.replace(s, "");
                    qEntry = s.replace("$", "").trim();
                }
                if (!(s = line.substring(9).trim()).isEmpty()) {
                    line = String.valueOf(line.substring(0, 9)) + s;
                    vjunk.add(line);
                }
            }
            boolean isSuppressed = true;
            boolean isNewCRLine = false;
            boolean isExtension = true;
            if (qEntry.length() > 0) {
                FindValue fv = new FindValue();
                fv.findValueInEntry(qEntry);
                String name = fv.name();
                if (fv.canGetV()) {
                    ContRecord cr = new ContRecord();
                    String crName = name;
                    String crText = fv.txt();
                    cr.setValues(crName, fv.symbol(), fv.s(), fv.units(), fv.ds(), fv.ref(), crText, contType, isSuppressed, isNewCRLine, isExtension);
                    this.crV.add(cr);
                    if (this.q_s.isEmpty()) {
                        this.q_s = cr.s();
                        this.dq_s = cr.ds();
                    }
                }
            }
            ++k;
        }
        this.lev.setValues(vjunk, 0);
        this.ion_s = ((String)this.lines.elementAt(0)).substring(76).trim();
        this.parseQV();
        k = 1;
        while (k < this.lines.size()) {
            Vector<String> com = new Vector<String>();
            if (((String)this.lines.elementAt(k)).substring(5, 8).equals(" CP") || ((String)this.lines.elementAt(k)).substring(5, 8).equals(" cP")) {
                com.addElement((String)this.lines.elementAt(k));
                int il = 1;
                while (k + il < this.lines.size() && (((String)this.lines.elementAt(k + il)).substring(6, 8).equals("CP") || ((String)this.lines.elementAt(k + il)).substring(6, 8).equals("cP")) && !((String)this.lines.elementAt(k + il)).substring(5, 6).equals(" ")) {
                    com.addElement((String)this.lines.elementAt(k + il));
                    ++il;
                }
                Comment c = new Comment();
                c.setValues(com);
                this.commentV.add(c);
            }
            ++k;
        }
        this.makeContRecordsVMap();
        this.lines.clear();
    }

    protected void parseQV() {
        if (this.q_s.length() > 0) {
            SDS2XDX s = new SDS2XDX();
            s.setValues(this.q_s, this.dq_s);
            this.q_f = (float)s.X();
            this.dq_f = (float)s.DX();
            if (this.dq_s.length() == 0) {
                this.dq_f = -1.0f;
            }
        } else {
            this.q_f = -1.0f;
            this.dq_f = -1.0f;
        }
        if (this.q_f != -1.0f) {
            this.qMev_s = "" + (float)Math.round(this.q_f * 10.0f) / 10000.0f;
            while (this.qMev_s.endsWith("0")) {
                this.qMev_s = this.qMev_s.substring(0, this.qMev_s.length() - 1);
            }
            if (this.qMev_s.endsWith(".")) {
                this.qMev_s = this.qMev_s.substring(0, this.qMev_s.length() - 1);
            }
        }
    }

    protected String rTrim(String s) {
        String st = s.trim();
        int i = s.indexOf(st);
        String srt = String.valueOf(s.substring(0, i)) + st;
        return srt;
    }

    public void setDMName(String s) {
        this.dm_name_s = s;
        this.dm.setName(s);
    }

    public void setDMValue(String s) {
        this.dm.setValue(s);
    }

    public void setDMUnc(String s) {
        this.dm.setUnc(s);
    }

    public DecayMode DM() {
        return this.dm;
    }

    public String DMName() {
        return this.dm_name_s;
    }

    public Nucleus nucleus() {
        return this.lev.nucleus();
    }

    public Level level() {
        return this.lev;
    }

    public String QS() {
        return this.q_s;
    }

    public String DQS() {
        return this.dq_s;
    }

    public String QUnicode() {
        String qu_s = "";
        if (this.q_s.length() > 0) {
            qu_s = this.dq_s.equals("GT") ? ">" + this.q_s + " keV" : (this.dq_s.equals("GE") ? String.valueOf('\u2265') + this.q_s + " keV" : (this.dq_s.equals("LT") ? ">" + this.q_s + " keV" : (this.dq_s.equals("LE") ? String.valueOf('\u2264') + this.q_s + " keV" : (this.dq_s.equals("AP") ? String.valueOf('\u2248') + this.q_s + " keV" : String.valueOf(this.q_s) + " keV " + this.dq_s))));
        }
        return qu_s;
    }

    public float QF() {
        return this.q_f;
    }

    public float DQF() {
        return this.dq_f;
    }

    public void setDQF(float f) {
        this.dq_f = f;
    }

    public String ionS() {
        return this.ion_s;
    }

    public String qMeVS() {
        return this.qMev_s;
    }

    public String getIndexS() {
        return this.indexS;
    }

    public void setIndexS(String s) {
        this.indexS = s;
    }

    @Override
    public String ES() {
        return "";
    }

    @Override
    public String DES() {
        return "";
    }

    @Override
    public float EF() {
        return 0.0f;
    }

    @Override
    public float DEF() {
        return 0.0f;
    }

    @Override
    public String RIS() {
        return "";
    }

    @Override
    public String DRIS() {
        return "";
    }

    @Override
    public String flag() {
        return "";
    }

    @Override
    public String q() {
        return "";
    }

    @Override
    public String coinS() {
        return "";
    }

    @Override
    public void addFlag(String s) {
    }
}
