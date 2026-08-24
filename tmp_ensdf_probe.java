import ensdfparser.ensdf.ENSDF;
import ensdfparser.nds.util.Str;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.ensdf.SDS2XDX;
import java.util.*;

public class tmp_ensdf_probe {
  static void test(String label, Vector<String> lines) {
    System.out.println("--- TEST: " + label + " ---");
    for (String l : lines) {
      System.out.println(l + " LEN=" + l.length());
    }
    try {
      ENSDF ens = new ENSDF();
      ens.setValues(lines);
      System.out.println("OK");
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  public static void main(String[] args) throws Exception {
    String d = "122CD"; String p = "122AG";
    String prefix = Str.makeENSDFLinePrefix(d, "   ");
    Vector<String> v1 = new Vector<>();
    v1.add(prefix + p + " B- DECAY");
    test("header only", v1);

    String pLine = Str.makeENSDFLinePrefix(p, "  P") + Str.repeat(" ", 71);
    pLine = EnsdfUtil.replaceEnergyOfRecordLine(pLine, "", "");
    pLine = EnsdfUtil.replaceJPIOfLevelLine(pLine, "1-");
    SDS2XDX T12 = SDS2XDX.parse("0.529","0.013",false);
    pLine = EnsdfUtil.replaceHalflifeOfLevelLine(pLine, T12.s(), T12.ds(), "S");
    SDS2XDX Q = SDS2XDX.parse("9510","40",false);
    pLine = EnsdfUtil.replaceC2SOfLevelLine(pLine, Q.s(), Q.ds());
    Vector<String> v2 = new Vector<>();
    v2.add(prefix + p + " B- DECAY");
    v2.add(pLine);
    test("header + parent", v2);

    String nLine = Str.makeENSDFLinePrefix(d, "  N") + Str.repeat(" ", 71);
    SDS2XDX BR = SDS2XDX.parse("100","",false).divided(100.0f);
    nLine = EnsdfUtil.replaceEnergyOfRecordLine(nLine, "", "");
    nLine = EnsdfUtil.replaceIEOfECBPLine(nLine, BR.s(), BR.ds());
    String lLine = Str.makeENSDFLinePrefix(d, "  L") + Str.repeat(" ", 71);
    lLine = EnsdfUtil.replaceEnergyOfRecordLine(lLine, "", "");
    lLine = EnsdfUtil.replaceEnergyOfRecordLine(lLine, "569.36","0.10");
    lLine = EnsdfUtil.replaceJPIOfLevelLine(lLine, "2+");
    String bLine = Str.makeENSDFLinePrefix(d, "  B") + Str.repeat(" ", 71);
    bLine = EnsdfUtil.replaceEnergyOfRecordLine(bLine, "", "");
    SDS2XDX TI = SDS2XDX.parse("5.0","0.2",false);
    bLine = EnsdfUtil.replaceRIOfRecordLine(bLine, TI.s(), TI.ds());
    Vector<String> v3 = new Vector<>();
    v3.add(prefix + p + " B- DECAY");
    v3.add(pLine); v3.add(nLine); v3.add(lLine); v3.add(bLine);
    test("full minimal", v3);
  }
}
