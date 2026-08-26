/*
 * Decompiled with CFR 0.152.
 */
package ensdfparser.nds.latex;

import ensdfparser.ensdf.Nucleus;
import ensdfparser.nds.ensdf.UserDictionary;
import ensdfparser.nds.util.Str;
import ensdfparser.nds.util.Word;
import ensdfparser.nds.util.numanl;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

public class Translator {
    protected static String[] Rkey;
    protected static String[] Rvalue;
    protected static String[] Nkey;
    protected static String[] Nvalue;
    protected static String[] Wkey;
    protected static String[] Wvalue;
    protected static String[] Tkey;
    protected static String[] Tvalue;
    private static boolean hasInit;
    private static ArrayList<String> operators;
    public static boolean canBeTranslatedAsDSID;
    private static boolean isTextFormat;
    protected static String line;

    static {
        hasInit = false;
        operators = new ArrayList<String>(Arrays.asList("LT", "LE", "GT", "GE", "AP"));
        canBeTranslatedAsDSID = true;
        isTextFormat = false;
        line = "";
    }

    public Translator() throws Exception {
        Translator.init();
    }

    public static void initTex(InputStream is) throws IOException {
        String line;
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        Vector<String> k = new Vector<String>();
        Vector<String> v = new Vector<String>();
        while ((line = br.readLine()) != null) {
            if (line.length() < 20 || line.charAt(0) == '!') continue;
            String ks = line.substring(0, 19).trim();
            String vs = line.substring(19, line.length()).trim();
            if (ks.length() <= 0 || vs.length() <= 0) continue;
            k.add(ks);
            v.add(vs);
        }
        br.close();
        Tkey = new String[v.size()];
        Tvalue = new String[v.size()];
        k.copyInto(Tkey);
        v.copyInto(Tvalue);
    }

    public static void initEns() throws IOException {
        Translator.initEnsText();
    }

    public static void initEnsText(String filePath) throws IOException {
        String p2;
        String p1;
        String line;
        InputStream is = Translator.getInputStream(filePath);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String sreac = br.readLine();
        String snucl = br.readLine();
        String sword = br.readLine();
        int rbeg = Integer.parseInt(sreac.substring(11, 16).trim());
        int rend = Integer.parseInt(sreac.substring(16, 22).trim());
        int nbeg = Integer.parseInt(snucl.substring(11, 16).trim());
        int nend = Integer.parseInt(snucl.substring(16, 22).trim());
        int wbeg = Integer.parseInt(sword.substring(11, 16).trim());
        int wend = Integer.parseInt(sword.substring(16, 22).trim());
        Vector<String> k = new Vector<String>();
        Vector<String> v = new Vector<String>();
        int x = 0;
        while (x < rend - rbeg + 1) {
            line = br.readLine();
            p1 = line.substring(0, 19).trim();
            p2 = line.substring(19, line.length()).trim();
            if (!p2.equals(".....")) {
                k.add(p1);
                v.add(p2);
            }
            ++x;
        }
        Rkey = new String[v.size()];
        Rvalue = new String[v.size()];
        k.copyInto(Rkey);
        v.copyInto(Rvalue);
        k = new Vector();
        v = new Vector();
        x = 0;
        while (x < nend - nbeg + 1) {
            line = br.readLine();
            p1 = line.substring(0, 19).trim();
            p2 = line.substring(19, line.length()).trim();
            if (!p2.equals(".....")) {
                k.add(p1);
                v.add(p2);
            }
            ++x;
        }
        Nkey = new String[v.size()];
        Nvalue = new String[v.size()];
        k.copyInto(Nkey);
        v.copyInto(Nvalue);
        k = new Vector();
        v = new Vector();
        x = 0;
        while (x < wend - wbeg + 1) {
            line = br.readLine();
            p1 = line.substring(0, 19).trim();
            p2 = line.substring(19, line.length()).trim();
            if (!p2.equals(".....")) {
                k.add(p1);
                v.add(p2);
            }
            ++x;
        }
        Wkey = new String[v.size()];
        Wvalue = new String[v.size()];
        k.copyInto(Wkey);
        v.copyInto(Wvalue);
        int i = Wkey.length - 1;
        while (i > 0) {
            int j = 0;
            while (j < i) {
                if (Wkey[j].length() < Wkey[j + 1].length()) {
                    String tmp = Wkey[j];
                    Translator.Wkey[j] = Wkey[j + 1];
                    Translator.Wkey[j + 1] = tmp;
                    tmp = Wvalue[j];
                    Translator.Wvalue[j] = Wvalue[j + 1];
                    Translator.Wvalue[j + 1] = tmp;
                }
                ++j;
            }
            --i;
        }
        br.close();
    }

    public static void initEnsText() throws IOException {
        Translator.initEnsText("ensdf_dic2.dat");
    }

    public static boolean hasInit0() {
        try {
            String s = "";
            s = Rkey[0];
            s = Rvalue[0];
            s = Nkey[0];
            s = Nvalue[0];
            s = Wkey[0];
            s = Wvalue[1];
            s = Tkey[0];
            s = Tvalue[0];
            return true;
        }
        catch (Exception exception) {
            return false;
        }
    }

    public static boolean hasInit() {
        return hasInit;
    }

    public static void init() throws IOException {
        if (hasInit) {
            return;
        }
        Translator.initEns();
        InputStream is = Translator.getInputStream("latex_dic.dat");
        if (is != null) {
            try {
                Translator.initTex(is);
                hasInit = true;
            }
            catch (Exception e) {
                System.out.print("Error: ");
                System.out.println(e.getLocalizedMessage());
            }
        } else {
            System.out.println("Error: LaTeX translation file not found.");
        }
    }

    public static InputStream getInputStream(String filePath) {
        InputStream is = null;
        try {
            URL url = ClassLoader.getSystemResource(filePath);
            if (url == null) {
                File f = new File(filePath);
                url = f.toURI().toURL();
            }
            is = url.openStream();
        }
        catch (Exception exception) {
            // empty catch block
        }
        if (is == null && (is = Translator.class.getClassLoader().getResourceAsStream(filePath)) == null) {
            System.out.println("Error: Translation file not found: " + filePath);
        }
        return is;
    }

    public static void loadENSDFDict(String filePath) throws Exception {
        if (filePath.trim().length() == 0) {
            System.out.println("using ENSDF translation dictionary from ensdf_dic2.dat");
            Translator.initEnsText();
        } else {
            System.out.println("using ENSDF translation dictionary from " + filePath);
            Translator.initEnsText(filePath);
        }
    }

    public static void loadLatexDict(String filePath) throws Exception {
        String tempPath = filePath;
        if (filePath.trim().length() == 0) {
            System.out.println("using LaTeX translation dictionary from latex_dic.dat");
            tempPath = "latex_dic.dat";
        } else {
            System.out.println("using LaTeX translation dictionary from " + filePath);
            tempPath = filePath.trim();
        }
        InputStream is = Translator.getInputStream(tempPath);
        if (is != null) {
            try {
                Translator.initTex(is);
            }
            catch (Exception e) {
                System.out.print("Error: ");
                System.out.println(e.getLocalizedMessage());
            }
        } else {
            System.out.println("Error: LaTeX translation file not found: " + tempPath);
        }
    }

    public static String processDot(String text) throws Exception {
        if ((text = Translator.process(text)).charAt(text.length() - 1) != '.') {
            text = String.valueOf(text) + ".";
        }
        return text;
    }

    public static String process(String text) throws Exception {
        String text3 = Translator.process(text, true);
        return text3;
    }

    public static String process(String text, boolean cap) throws Exception {
        canBeTranslatedAsDSID = true;
        String text2 = Translator.procGenCom(text, 0, cap);
        String text3 = Translator.translate(text2);
        return text3;
    }

    protected static boolean match(String s1, String s2, int ch) {
        if (s1 == null || s2 == null || ch < 0) {
            return false;
        }
        if (s2.length() < s1.length() + ch) {
            return false;
        }
        int x = 0;
        while (x < s1.length()) {
            char c;
            if (s1.charAt(x) == '_' ? (c = s2.charAt(x + ch)) != ' ' && c != '.' && c != ',' && c != '\n' && c != '\t' && c != '\r' : s1.charAt(x) != s2.charAt(x + ch)) {
                return false;
            }
            ++x;
        }
        return true;
    }

    private static String printTXT(String txt, boolean inMath) {
        if (!inMath) {
            if (txt.contains("-") && txt.contains("+")) {
                txt = txt.replace("-", "{\\textminus}");
            }
        } else if (txt.equals("-") || isTextFormat) {
            isTextFormat = false;
        } else {
            if (txt.indexOf("-") >= 0 && !txt.contains("--")) {
                txt = txt.replace("-", "$-$");
            }
            txt = "\\textnormal{" + txt + "}";
        }
        return txt;
    }

    public static String translate(String text) {
        return Translator.translate(text, 0, text.length() - 1);
    }

    public static String translate(String text, int start, int end) {
        int index = start;
        int nest = 0;
        String output = "";
        String txt = "";
        char c = '\u0000';
        boolean inMath = false;
        boolean endMath = false;
        boolean isAfterSuper = false;
        boolean isAfterSub = false;
        boolean isSub = false;
        boolean isSuper = false;
        int mathnest = -1;
        int scriptNest = -1;
        int nsingleQuote = 0;
        int ndoubleQuote = 0;
        int iFirstMathNest = -1;
        if (start > end) {
            return text;
        }
        isTextFormat = false;
        while (index < text.length() && index >= start && index <= end) {
            int p;
            c = text.charAt(index);
            if ((c == 'J' || c == 'K') && "|p".regionMatches(0, text, index + 1, 2)) {
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                if (endMath) {
                    inMath = false;
                    endMath = false;
                    mathnest = -1;
                    iFirstMathNest = -1;
                    output = String.valueOf(output) + "}";
                }
                output = inMath ? String.valueOf(output) + c + "^{\\pi}" : String.valueOf(output) + "\\ensuremath{" + c + "^{\\pi}}";
                index += 3;
                continue;
            }
            if ("{+".regionMatches(0, text, index, 2)) {
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                endMath = false;
                if (!inMath) {
                    output = String.valueOf(output) + "\\ensuremath{";
                    inMath = true;
                    endMath = false;
                    mathnest = nest + 1;
                    iFirstMathNest = nest;
                } else {
                    ++mathnest;
                }
                if (isAfterSuper) {
                    output = String.valueOf(output) + "~";
                }
                isSuper = true;
                scriptNest = scriptNest > 0 ? ++scriptNest : 1;
                output = String.valueOf(output) + "^{";
                ++nest;
                index += 2;
                continue;
            }
            if ("{-".regionMatches(0, text, index, 2)) {
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                endMath = false;
                if (!inMath) {
                    output = String.valueOf(output) + "\\ensuremath{";
                    inMath = true;
                    endMath = false;
                    mathnest = nest + 1;
                    iFirstMathNest = nest;
                } else {
                    ++mathnest;
                }
                if (isAfterSub) {
                    output = String.valueOf(output) + "~";
                }
                isSub = true;
                scriptNest = scriptNest > 0 ? ++scriptNest : 1;
                output = String.valueOf(output) + "_{";
                ++nest;
                index += 2;
                continue;
            }
            if (!" ".regionMatches(0, text, index, 1)) {
                "=".regionMatches(0, text, index, 1);
            }
            if ("{I".regionMatches(0, text, index, 2) || "{i".regionMatches(0, text, index, 2)) {
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                if (scriptNest > 0) {
                    ++scriptNest;
                }
                if (endMath) {
                    inMath = false;
                    endMath = false;
                    mathnest = -1;
                    iFirstMathNest = -1;
                    output = String.valueOf(output) + "}";
                }
                isTextFormat = true;
                output = String.valueOf(output) + "\\textit{";
                ++nest;
                index += 2;
                continue;
            }
            if ("{B".regionMatches(0, text, index, 2) || "{b".regionMatches(0, text, index, 2)) {
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                if (scriptNest > 0) {
                    ++scriptNest;
                }
                if (endMath) {
                    inMath = false;
                    endMath = false;
                    mathnest = -1;
                    iFirstMathNest = -1;
                    output = String.valueOf(output) + "}";
                }
                isTextFormat = true;
                output = String.valueOf(output) + "\\textbf{";
                ++nest;
                index += 2;
                continue;
            }
            if ("{U".regionMatches(0, text, index, 2) || "{u".regionMatches(0, text, index, 2)) {
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                if (scriptNest > 0) {
                    ++scriptNest;
                }
                if (endMath) {
                    inMath = false;
                    endMath = false;
                    mathnest = -1;
                    iFirstMathNest = -1;
                    output = String.valueOf(output) + "}";
                }
                isTextFormat = true;
                output = String.valueOf(output) + " \\underline{";
                ++nest;
                index += 2;
                continue;
            }
            if ("^{".regionMatches(0, text, index, 2)) {
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                if ((p = Str.indexesOfBrackets(text, "{", index)[1]) > index + 2) {
                    String s = text.substring(index + 2, p);
                    output = String.valueOf(output) + "\\ensuremath{" + s + "}";
                    index = p + 1;
                    continue;
                }
            }
            if ("^[".regionMatches(0, text, index, 2)) {
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                if ((p = Str.indexesOfBrackets(text, "[", index)[1]) > index + 2) {
                    String s = text.substring(index + 2, p);
                    output = String.valueOf(output) + s;
                    index = p + 1;
                    continue;
                }
            }
            if ("\"".regionMatches(0, text, index, 1)) {
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                if ((ndoubleQuote = index > 1 && text.charAt(index - 1) == ' ' ? 0 : 1) == 0) {
                    txt = String.valueOf(txt) + "``";
                    ++ndoubleQuote;
                } else {
                    txt = String.valueOf(txt) + "''";
                    --ndoubleQuote;
                }
                ++index;
                continue;
            }
            if ("|\"{".regionMatches(0, text, index, 3)) {
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                endMath = false;
                if (!inMath) {
                    output = String.valueOf(output) + "\\ensuremath{";
                    inMath = true;
                    endMath = false;
                    mathnest = nest + 1;
                    iFirstMathNest = nest;
                }
                if (scriptNest > 0) {
                    ++scriptNest;
                }
                output = String.valueOf(output) + "\\bar{";
                ++nest;
                index += 3;
                continue;
            }
            if ("|%{".regionMatches(0, text, index, 3) || "|6{".regionMatches(0, text, index, 3)) {
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                endMath = false;
                if (!inMath) {
                    output = String.valueOf(output) + "\\ensuremath{";
                    inMath = true;
                    endMath = false;
                    mathnest = nest + 1;
                    iFirstMathNest = nest;
                }
                if (scriptNest > 0) {
                    ++scriptNest;
                }
                output = String.valueOf(output) + "\\sqrt{";
                ++nest;
                index += 3;
                continue;
            }
            if ("|%[".regionMatches(0, text, index, 3) || "|6[".regionMatches(0, text, index, 3)) {
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                endMath = false;
                if (!inMath) {
                    output = String.valueOf(output) + "\\ensuremath{";
                    inMath = true;
                    endMath = false;
                    mathnest = nest + 1;
                    iFirstMathNest = nest;
                }
                output = String.valueOf(output) + "\\sqrt[";
                index += 3;
                continue;
            }
            if ("'".regionMatches(0, text, index, 1)) {
                boolean inDSID = false;
                List<String> list = Arrays.asList("')", "'|g", "'n)", "'p)", "'|a)", "'2p", "'2n", "'3p", "'3n");
                ArrayList<String> primes = new ArrayList<String>(list);
                int i = 0;
                while (i < primes.size()) {
                    if (text.indexOf(primes.get(i), index) == index) {
                        inDSID = true;
                        break;
                    }
                    ++i;
                }
                if (index > 1 && text.charAt(index - 1) == ' ') {
                    inDSID = false;
                }
                if (inDSID) {
                    if (txt.length() > 0) {
                        output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                        txt = "";
                    }
                    if (endMath) {
                        inMath = false;
                        endMath = false;
                        mathnest = -1;
                        iFirstMathNest = -1;
                        output = String.valueOf(output) + "}";
                    }
                    output = inMath ? String.valueOf(output) + "'" : String.valueOf(output) + "\\ensuremath{'}";
                    ++index;
                    continue;
                }
                if (index > 1 && text.charAt(index - 1) == ' ') {
                    txt = String.valueOf(txt) + "`";
                    ++nsingleQuote;
                } else if (nsingleQuote > 0) {
                    txt = String.valueOf(txt) + "'";
                    --nsingleQuote;
                } else {
                    txt = String.valueOf(txt) + "$'$";
                }
                ++index;
                continue;
            }
            if (Translator.isPI(text, index)) {
                char prevChar = '\u0000';
                char nextChar = '\u0000';
                String bl = "";
                String br = "";
                if (index > 0) {
                    prevChar = text.charAt(index - 1);
                }
                if (index < text.length() - 1) {
                    nextChar = text.charAt(index + 1);
                }
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                if (endMath) {
                    inMath = false;
                    endMath = false;
                    mathnest = -1;
                    iFirstMathNest = -1;
                    output = String.valueOf(output) + "}";
                }
                if (prevChar == '(' && nextChar == ')' && output.charAt(output.length() - 1) == '(') {
                    bl = "(";
                    br = ")";
                    output = output.substring(0, output.length() - 1);
                }
                output = inMath ? String.valueOf(output) + "^{" + bl + text.charAt(index) + br + "}" : String.valueOf(output) + "\\ensuremath{^{" + bl + text.charAt(index) + br + "}}";
                ++index;
                if (br.length() != 1) continue;
                ++index;
                continue;
            }
            if (text.charAt(index) == '\\' && index < text.length() - 1 && (text.charAt(index + 1) == '{' || text.charAt(index + 1) == ' ')) {
                ++index;
                continue;
            }
            if (index < text.length() && text.charAt(index) == '{') {
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                if (endMath) {
                    isSuper = false;
                    isSub = false;
                    inMath = false;
                    endMath = false;
                    mathnest = -1;
                    iFirstMathNest = -1;
                    output = String.valueOf(output) + "}";
                } else if (inMath) {
                    ++mathnest;
                }
                if (scriptNest > 0) {
                    ++scriptNest;
                }
                output = String.valueOf(output) + "{";
                ++nest;
                ++index;
                continue;
            }
            if (index < text.length() && text.charAt(index) == '^') {
                if (txt.length() > 0) {
                    output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                    txt = "";
                }
                if (endMath) {
                    inMath = false;
                    endMath = false;
                    mathnest = -1;
                    iFirstMathNest = -1;
                    output = String.valueOf(output) + "}";
                }
                output = inMath ? (index < text.length() - 1 && text.charAt(index + 1) == '{' ? String.valueOf(output) + "\\hat" : String.valueOf(output) + "\\hat{}") : String.valueOf(output) + "\\^";
                ++index;
                continue;
            }
            if (index < text.length() && text.charAt(index) == '}') {
                if (txt.length() > 0) {
                    if (!inMath) {
                        if (txt.contains("-") && txt.contains("+")) {
                            txt = txt.replace("-", "{\\textminus}");
                        }
                        output = String.valueOf(output) + txt;
                    } else if (txt.equals("-") || isTextFormat) {
                        output = String.valueOf(output) + txt;
                        isTextFormat = false;
                    } else {
                        if (txt.indexOf("-") >= 0 && !txt.contains("--")) {
                            txt = txt.replace("-", "$-$");
                        }
                        output = String.valueOf(output) + "\\textnormal{" + txt + "}";
                    }
                    txt = "";
                }
                if (endMath) {
                    inMath = false;
                    endMath = false;
                    mathnest = -1;
                    iFirstMathNest = -1;
                    output = String.valueOf(output) + "}";
                }
                if (inMath && nest <= iFirstMathNest + 1) {
                    endMath = true;
                }
                if (nest > 0) {
                    output = String.valueOf(output) + "}";
                    if (--nest == 0 && isTextFormat) {
                        isTextFormat = false;
                    }
                }
                if (scriptNest > 0 && --scriptNest == 0) {
                    if (isSuper) {
                        isAfterSuper = true;
                    }
                    if (isSub) {
                        isAfterSub = true;
                    }
                }
                ++index;
                continue;
            }
            if (endMath) {
                inMath = false;
                endMath = false;
                isSuper = false;
                isSub = false;
                isAfterSuper = false;
                isAfterSub = false;
                mathnest = -1;
                iFirstMathNest = -1;
                output = String.valueOf(output) + "}";
            }
            boolean match = false;
            int x = 0;
            while (x < Tkey.length) {
                if (Tkey[x].regionMatches(0, text, index, Tkey[x].length())) {
                    if (txt.length() > 0) {
                        output = String.valueOf(output) + Translator.printTXT(txt, inMath);
                        txt = "";
                    }
                    String key = Tkey[x];
                    String val = Tvalue[x];
                    boolean isGoodMatch = true;
                    if (index + Tkey[x].length() - 1 > end) {
                        String tempS = text.substring(index, end + 1);
                        if (key.charAt(tempS.length()) == ' ' && key.length() == val.length() && val.charAt(tempS.length()) == ' ') {
                            key = key.substring(0, tempS.length());
                            val = val.substring(0, tempS.length());
                        } else {
                            isGoodMatch = false;
                        }
                    }
                    if (isGoodMatch) {
                        output = String.valueOf(output) + val;
                        index += key.length();
                        match = true;
                        break;
                    }
                }
                ++x;
            }
            if (match) continue;
            if (index < text.length()) {
                c = text.charAt(index);
                if (c == '-') {
                    char prevChar = '\u0000';
                    char nextChar = '\u0000';
                    boolean isMinusSign = false;
                    if (index > 0) {
                        prevChar = text.charAt(index - 1);
                    }
                    if (index < text.length() - 1) {
                        nextChar = text.charAt(index + 1);
                    }
                    if (prevChar == ' ' && nextChar == ' ') {
                        isMinusSign = true;
                    } else if (prevChar == '(' && nextChar == ')') {
                        isMinusSign = true;
                    } else if (prevChar == '[' && nextChar == ']') {
                        isMinusSign = true;
                    } else if (Character.isLetter(prevChar) || Character.isLetter(nextChar)) {
                        isMinusSign = false;
                        String ops = "=><~?";
                        int i = index - 1;
                        int op = 32;
                        while (i >= 0) {
                            char cc = text.charAt(i);
                            if (cc == ' ' || index - i > 30) break;
                            if (ops.indexOf(cc) >= 0) {
                                op = cc;
                                break;
                            }
                            --i;
                        }
                        if (op != 32) {
                            isMinusSign = true;
                        }
                    } else if (Character.isDigit(prevChar) != Character.isDigit(nextChar)) {
                        isMinusSign = true;
                        int i = index - 2;
                        String s = "";
                        while (i >= 0) {
                            if (!Character.isDigit(text.charAt(i)) && text.charAt(i) != '.') {
                                s = text.substring(i + 1, index);
                                break;
                            }
                            --i;
                        }
                        int count = index - 1 - i;
                        if (!(count < 3 && !Str.isNumeric(s) || nextChar != ',' && nextChar != ' ')) {
                            isMinusSign = false;
                        }
                    } else if (prevChar == '=') {
                        isMinusSign = true;
                    }
                    txt = isMinusSign && !inMath ? String.valueOf(txt) + "{\\textminus}" : String.valueOf(txt) + c;
                } else {
                    txt = String.valueOf(txt) + c;
                }
            }
            ++index;
        }
        if (txt.length() > 0) {
            output = String.valueOf(output) + Translator.printTXT(txt, inMath);
        }
        while (nest > 0) {
            output = String.valueOf(output) + "}";
            --nest;
        }
        if (inMath) {
            output = String.valueOf(output) + "}";
        }
        output = output.replace("\\ensuremath{}", "");
        output = Str.fillLongSpace(output);
        return output;
    }

    public static String impComVal(String x, String dx) {
        if (dx == null || dx.isEmpty()) {
            return x;
        }
        if (Translator.isNumericUnc(dx.trim().charAt(0))) {
            return String.valueOf(x) + "  {I" + dx + "}";
        }
        return String.valueOf(dx) + " " + x;
    }

    public static boolean isNumericNum(char x) {
        char[] nums = new char[]{'1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '-', '+', 'E', '(', ')', '.', 'x'};
        int i = 0;
        while (i < nums.length) {
            if (nums[i] == x) {
                return true;
            }
            ++i;
        }
        return false;
    }

    public static boolean isNumericUnc(char x) {
        char[] nums = new char[]{'1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '-', '+'};
        int i = 0;
        while (i < nums.length) {
            if (nums[i] == x) {
                return true;
            }
            ++i;
        }
        return false;
    }

    public static boolean containsDigits(String x) {
        return x.contains("0") || x.contains("1") || x.contains("2") || x.contains("3") || x.contains("4") || x.contains("5") || x.contains("6") || x.contains("7") || x.contains("8") || x.contains("9");
    }

    public static int unitLength(String s) {
        String[] units = new String[]{" S ", " M ", " H ", " D ", " Y ", " MS ", " US ", " MEV ", " KEV ", " EV ", " NS ", " PS ", " FS ", " MIN ", " MB ", " KOE ", " UG ", " MG ", " FM ", " MILLI ", " CM ", " CM**2 ", " CM2 ", " FM**2 ", " SR ", " B ", " AS ", " DEG ", " min ", " |ms ", " eV ", " |mg ", " m ", " cm{+2} ", " cm{+2} ", " fm{+2} ", " |' "};
        int i = 0;
        while (i < units.length) {
            if (s.toLowerCase().contains(units[i].toLowerCase())) {
                return units[i].length() - 1;
            }
            ++i;
        }
        return 0;
    }

    public static String checkXDX(String line) {
        int ch = 0;
        int d = 0;
        int i = 1;
        while (i < line.length()) {
            if (!Translator.isNumericNum(line.charAt(ch = i++))) break;
        }
        if (line.charAt(ch) != ' ' || !Translator.containsDigits(line.substring(0, ch))) {
            return "";
        }
        String temp = line.substring(0, ch).replace("(", "");
        if (!Str.isNumeric(temp = temp.replace(")", "").trim())) {
            return "";
        }
        ch = ch + 8 <= line.length() ? (ch += Translator.unitLength(line.substring(ch, ch + 8))) : (ch += Translator.unitLength(line.substring(ch)));
        d = ch;
        int i2 = ch + 1;
        while (i2 < line.length()) {
            if (!Translator.isNumericUnc(line.charAt(ch = i2++))) break;
        }
        if (ch >= line.length() || !Translator.containsDigits(line.substring(d, ch))) {
            return "";
        }
        if (line.charAt(ch) != ' ' && line.charAt(ch) != '.' && line.charAt(ch) != ',' && line.charAt(ch) != ';') {
            return "";
        }
        if (line.charAt(ch) == '.' && ch < line.length() - 1 && Character.isDigit(line.charAt(ch + 1))) {
            return "";
        }
        line = line.substring(0, ch);
        StringBuffer sb = new StringBuffer(line);
        sb.insert(d + 1, "{I");
        line = sb.toString();
        line = String.valueOf(line) + "}";
        return line;
    }

    public static String halfLife(String s) throws IOException {
        String val = "";
        String unt = "";
        String unc = "";
        String[] part = s.split("[\\ ]+");
        if (part.length > 0) {
            val = part[0];
        }
        if (part.length > 1) {
            unt = part[1];
        }
        if (part.length > 2) {
            unc = part[2];
        }
        return Translator.halfLife(val.trim(), unc.trim(), unt.trim());
    }

    public static String halfLife(String ts, String dts, String tus) throws IOException {
        return Translator.printNumber(ts, dts, tus);
    }

    public static String printNumber(String x, String dx, String unit) throws IOException {
        return Translator.printNumber(x, dx, unit, false);
    }

    public static String printNumber(String x, String dx) throws IOException {
        return Translator.printNumber(x, dx, "");
    }

    public static String printNumber(String x, String dx, boolean equals) throws IOException {
        return Translator.printNumber(x, dx, "", equals);
    }

    public static String printNumber(String x, String dx, String unit, boolean equals) throws IOException {
        String symbol = "";
        String out = "";
        String s = Str.addMissingZero(x);
        String ds = dx;
        if (s.toUpperCase().equals("STABLE")) {
            return s.toLowerCase();
        }
        if (ds.length() <= 0) {
            s = s.replace("<", "$<$");
            s = s.replace(">", "$>$");
        }
        if (equals) {
            symbol = "=";
        }
        if (ds.equals("LE")) {
            ds = "";
            symbol = "$\\leq$";
        } else if (ds.equals("GE")) {
            ds = "";
            symbol = "$\\geq$";
        } else if (ds.equals("LT")) {
            ds = "";
            symbol = "$<$";
        } else if (ds.equals("GT")) {
            ds = "";
            symbol = "$>$";
        } else if (ds.equals("AP")) {
            ds = "";
            symbol = "$\\approx$";
        } else if (!ds.isEmpty() && !Str.isNumeric(ds.replace("+", "").replace("-", ""))) {
            try {
                ds = Translator.process(ds);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        out = String.valueOf(out) + symbol + Translator.exponential(s);
        if (unit.length() > 0) {
            out = String.valueOf(out) + " " + Translator.halfLifeUnits(unit);
        }
        if (ds.length() > 0) {
            out = String.valueOf(out) + " {\\it " + ds.replace("-", "\\textminus") + "}";
        }
        return out.trim();
    }

    public static String printNumberAsIs(String x, String dx, String unit) throws IOException {
        return Translator.printNumberAsIs(x, dx, unit, false);
    }

    public static String printNumberAsIs(String x, String dx) throws IOException {
        return Translator.printNumberAsIs(x, dx, "");
    }

    public static String printNumberAsIs(String x, String dx, boolean equals) throws IOException {
        return Translator.printNumberAsIs(x, dx, "", equals);
    }

    public static String printNumberAsIs(String x, String dx, String unit, boolean equals) throws IOException {
        String symbol = "";
        String out = "";
        String s = Str.addMissingZero(x);
        String ds = dx;
        if (s.toUpperCase().equals("STABLE")) {
            return s.toLowerCase();
        }
        if (equals) {
            symbol = "=";
        }
        if (ds.length() > 0 && !Str.isNumeric(ds) && !ds.equalsIgnoreCase("SY")) {
            symbol = ds;
            ds = "";
        }
        out = String.valueOf(out) + symbol + s;
        if (unit.length() > 0) {
            out = String.valueOf(out) + " " + unit;
        }
        if (ds.length() > 0) {
            out = String.valueOf(out) + " " + ds;
        }
        return out.trim();
    }

    public static String printNumberSuperscipt(String x, String dx, String unit, boolean equals) throws IOException {
        String symbol = "";
        String out = "";
        String s = Str.addMissingZero(x);
        String ds = dx;
        if (s.toUpperCase().equals("STABLE")) {
            return s.toLowerCase();
        }
        if (ds.equals("LE")) {
            ds = "";
            symbol = "$\\leq$";
        } else if (ds.equals("GE")) {
            ds = "";
            symbol = "$\\geq$";
        } else if (ds.equals("LT")) {
            ds = "";
            symbol = "$<$";
        } else if (ds.equals("GT")) {
            ds = "";
            symbol = "$>$";
        } else if (ds.equals("AP")) {
            ds = "";
            symbol = "$\\approx$";
        } else if (equals) {
            symbol = "=";
        }
        if (symbol.length() > 0) {
            out = String.valueOf(out) + symbol;
        }
        out = String.valueOf(out) + Translator.exponential(s);
        if (unit.length() > 0) {
            out = String.valueOf(out) + " " + unit + " ";
            if (ds.length() > 0) {
                out = String.valueOf(out) + "{\\it " + ds + "}";
            }
        } else if (ds.length() > 0) {
            if (ds.contains("+") && ds.contains("-")) {
                int plus = ds.indexOf("+");
                int minus = ds.indexOf("-");
                out = String.valueOf(out) + "\\ensuremath{^{" + ds.substring(plus + 1, minus) + "}_{" + ds.substring(minus + 1) + "}}";
            } else {
                out = String.valueOf(out) + " \\ensuremath{^{" + ds + "}}";
            }
        }
        return out.trim();
    }

    public static String halfLifeUnits(String s) {
        if (s.equals("meV") || s.equals("mEV")) {
            return "meV";
        }
        if ((s = s.toUpperCase()).equals("US")) {
            return "\\ensuremath{\\mu}s";
        }
        if (s.equals("M")) {
            return "min";
        }
        if (s.equals("EV")) {
            return "eV";
        }
        if (s.equals("KEV")) {
            return "keV";
        }
        if (s.equals("MEV")) {
            return "MeV";
        }
        return s.toLowerCase();
    }

    public static String halfLifeUnitsLowerCase(String s) {
        if (s.equals("meV") || s.equals("mEV")) {
            return "meV";
        }
        if ((s = s.toUpperCase()).equals("US")) {
            return "|ms";
        }
        if (s.equals("M")) {
            return "min";
        }
        if (s.equals("EV")) {
            return "eV";
        }
        if (s.equals("KEV")) {
            return "keV";
        }
        if (s.equals("MEV")) {
            return "MeV";
        }
        return s.toLowerCase();
    }

    public static String value(String val, String unc) {
        return Translator.value(val, unc, 2, -2);
    }

    public static String valueAsIs(String val, String unc) {
        String ret = "";
        val = Str.addMissingZero(val);
        if (unc.equals("LT")) {
            ret = String.valueOf(ret) + "\\ensuremath{<}";
        } else if (unc.equals("LE")) {
            ret = String.valueOf(ret) + "\\ensuremath{\\leq}";
        } else if (unc.equals("GT")) {
            ret = String.valueOf(ret) + "\\ensuremath{>}";
        } else if (unc.equals("GE")) {
            ret = String.valueOf(ret) + "\\ensuremath{\\geq}";
        } else if (unc.equals("AP")) {
            ret = String.valueOf(ret) + "\\ensuremath{\\approx}";
        }
        return String.valueOf(ret) + Translator.exponential(val);
    }

    public static String value(String val, String unc, int minPositivePower, int maxNegativePower) {
        int n = val.toUpperCase().indexOf("E");
        if (n <= 0 || n == val.length() - 1) {
            return Translator.plainValue(val, unc);
        }
        String expS = val.substring(n + 1).trim();
        if (!Str.isInteger(expS)) {
            return Translator.valueAsIs(val, unc);
        }
        n = Integer.parseInt(expS);
        if (minPositivePower <= 0) {
            minPositivePower = 2;
        }
        if (maxNegativePower >= 0) {
            maxNegativePower = -2;
        }
        if (n < 0 && n > maxNegativePower || n >= 0 && n < minPositivePower) {
            return Translator.plainValue(val, unc);
        }
        return Translator.valueAsIs(val, unc);
    }

    public static String plainValue(String val, String unc, int maxLen) {
        String out = Translator.plainValue(val, unc);
        int pe = val.toUpperCase().indexOf("E");
        if (pe <= 0) {
            return out;
        }
        int len = val.length() + 2;
        if (!Str.isNumeric(unc) && unc.length() > 0) {
            ++len;
        }
        if (len <= maxLen) {
            return out;
        }
        return Translator.value(val, unc);
    }

    public static int plainValueLen(String val, String unc) {
        int len = val.length();
        if (!Str.isNumeric(unc) && unc.length() > 0) {
            ++len;
        }
        if (val.toUpperCase().indexOf("E") > 0) {
            len += 2;
        }
        return len;
    }

    public static String plainValue(String val, String unc) {
        String ret = "";
        val = Str.addMissingZero(val);
        if (unc.equals("LT")) {
            ret = String.valueOf(ret) + "\\ensuremath{<}";
        } else if (unc.equals("LE")) {
            ret = String.valueOf(ret) + "\\ensuremath{\\leq}";
        } else if (unc.equals("GT")) {
            ret = String.valueOf(ret) + "\\ensuremath{>}";
        } else if (unc.equals("GE")) {
            ret = String.valueOf(ret) + "\\ensuremath{\\geq}";
        } else if (unc.equals("AP")) {
            ret = String.valueOf(ret) + "\\ensuremath{\\approx}";
        }
        int pe = val.toUpperCase().indexOf("E");
        if (pe <= 0) {
            return String.valueOf(ret) + val;
        }
        int pp = val.indexOf(".");
        int pow = 0;
        String base = val.substring(0, pe);
        try {
            pow = Integer.parseInt(val.substring(pe + 1));
        }
        catch (Exception e) {
            return String.valueOf(ret) + Translator.exponential(val);
        }
        if (pp < 0) {
            pp = pe;
        }
        base = base.replace(".", "");
        if ((pp += pow) <= 0) {
            while (pp < 0) {
                base = "0" + base;
                ++pp;
            }
            base = "0." + base;
        } else if (pp < base.length()) {
            base = String.valueOf(base.substring(0, pp)) + "." + base.substring(pp);
        } else {
            int len = base.length();
            while (pp > len) {
                base = String.valueOf(base) + "0";
                --pp;
            }
        }
        return String.valueOf(ret) + base;
    }

    public static String orbital(String orbitalS) {
        String out = "";
        String s = orbitalS.toUpperCase();
        if (!orbitalS.equals(s)) {
            return orbitalS;
        }
        if ((s = s.trim()).length() < 4) {
            return orbitalS;
        }
        String shell = "";
        String js = "";
        boolean isJS = false;
        int i = 0;
        while (i < s.length()) {
            block15: {
                char c;
                block14: {
                    c = s.charAt(i);
                    isJS = false;
                    if (!Character.isDigit(c) && c != '/') break block14;
                    js = String.valueOf(js) + c;
                    isJS = true;
                    if (i < s.length() - 1) break block15;
                }
                if (js.length() > 0 && shell.length() > 0) {
                    out = String.valueOf(out) + shell.toLowerCase() + "{-" + js + "}";
                } else if (js.length() > 0) {
                    out = String.valueOf(out) + js;
                } else if (shell.length() > 0) {
                    out = String.valueOf(out) + shell;
                }
                shell = "";
                js = "";
                if (Character.isLetter(c)) {
                    shell = String.valueOf(c);
                } else if (!isJS) {
                    out = String.valueOf(out) + c;
                }
            }
            ++i;
        }
        return out;
    }

    public static String spin(String spinText) {
        String out = "";
        String text = spinText.trim();
        String psymbol = "+";
        String msymbol = "-";
        char nextChar = '\u0000';
        char prevChar = '\u0000';
        int i = 0;
        while (i < text.length()) {
            if (i > 0) {
                prevChar = text.charAt(i - 1);
            }
            nextChar = i < text.length() - 1 ? text.charAt(i + 1) : (char)'\u0000';
            if (text.charAt(i) == '&') {
                out = String.valueOf(out) + "\\&";
            } else if (i < text.length() - 3 && text.substring(i, i + 3).equalsIgnoreCase("and")) {
                out = String.valueOf(out) + "\\&";
                i += 2;
            } else if (i < text.length() - 2 && text.substring(i, i + 2).equalsIgnoreCase("or")) {
                out = String.valueOf(out) + "or";
                ++i;
            } else {
                out = text.charAt(i) == ';' || text.charAt(i) == ':' ? String.valueOf(out) + " to " : ((text.charAt(i) == '+' || text.charAt(i) == '-') && i < text.length() - 1 ? (i == 0 || Character.isLetterOrDigit(nextChar) && Character.isLetterOrDigit(prevChar) ? (text.charAt(i) == '+' ? String.valueOf(out) + "PSYMBOL" : String.valueOf(out) + "MPSYMBOL") : (text.charAt(i) == '+' && !Translator.isPIplus(spinText, i) ? String.valueOf(out) + "PSYMBOL" : String.valueOf(out) + text.charAt(i))) : String.valueOf(out) + text.charAt(i));
            }
            ++i;
        }
        out = out.replace("|<", "LE");
        out = out.replace("|>", "GE");
        out = out.replace("<", "LT");
        out = out.replace(">", "GT");
        out = Translator.replaceAndSqueeze(out, "LE", "\\ensuremath{\\leq}");
        out = Translator.replaceAndSqueeze(out, "GE", "\\ensuremath{\\geq}");
        out = Translator.replaceAndSqueeze(out, "LT", "\\ensuremath{<}");
        out = Translator.replaceAndSqueeze(out, "GT", "\\ensuremath{>}");
        out = Translator.replaceAndSqueeze(out, "AP", "\\ensuremath{\\approx}");
        out = out.replace("TO", "to");
        out = out.replace("To", "to");
        if (text.lastIndexOf("(+)") > 0) {
            out = out.replace("(+)", "(P)");
        }
        out = out.replace("+", "\\ensuremath{^{+}}");
        out = out.replace("(P)", "\\ensuremath{^{(+)}}");
        if (text.lastIndexOf("(-)") > 0) {
            out = out.replace("(-)", "(M)");
        }
        out = out.replace("-", "\\ensuremath{^{-}}");
        out = out.replace("(M)", "\\ensuremath{^{(-)}}");
        out = out.replaceAll("PSYMBOL", psymbol);
        out = out.replaceAll("MSYMBOL", msymbol);
        out.replace("NOT", "not");
        return out;
    }

    public static String plainSpin(String text) {
        String out = "";
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '&') {
                out = String.valueOf(out) + "\\&";
            } else if (i < text.length() - 3 && text.charAt(i) == 'a') {
                if (text.substring(i, i + 3).equalsIgnoreCase("and")) {
                    out = String.valueOf(out) + "\\&";
                    i += 2;
                }
            } else {
                out = text.charAt(i) == ';' || text.charAt(i) == ':' ? String.valueOf(out) + " to " : String.valueOf(out) + text.charAt(i);
            }
            ++i;
        }
        out = out.replace("|<", "LE");
        out = out.replace("|>", "GE");
        out = out.replace("<", "LT");
        out = out.replace(">", "GT");
        out = Translator.replaceAndSqueeze(out, "LE", "\\ensuremath{\\leq}");
        out = Translator.replaceAndSqueeze(out, "GE", "\\ensuremath{\\geq}");
        out = Translator.replaceAndSqueeze(out, "LT", "\\ensuremath{<}");
        out = Translator.replaceAndSqueeze(out, "GT", "\\ensuremath{>}");
        out = Translator.replaceAndSqueeze(out, "AP", "\\ensuremath{\\approx}");
        out = out.replace("TO", "to");
        out = out.replace("To", "to");
        return out;
    }

    public static String replaceAndSqueeze(String str, String oldWord, String newWord) {
        String out = "";
        String temp = str;
        int p = str.indexOf(oldWord);
        if (p < 0) {
            return str;
        }
        do {
            out = String.valueOf(out) + temp.substring(0, p).trim() + newWord;
            if ((temp = temp.substring(p + oldWord.length()).trim()).length() != 0) continue;
            return out;
        } while ((p = temp.indexOf(oldWord)) >= 0);
        if (temp.length() > 0) {
            out = String.valueOf(out) + temp;
        }
        return out;
    }

    public static String removeKeynos(String text) {
        String out = "";
        boolean isKeyno = false;
        int keynoLength = 0;
        int i = 0;
        while (i < text.length()) {
            int j;
            keynoLength = 0;
            if (i < text.length() - 8) {
                isKeyno = true;
                keynoLength = 7;
                if (text.charAt(i) != '1' && text.charAt(i) != '2') {
                    isKeyno = false;
                }
                j = 1;
                while (j <= 3) {
                    if (text.charAt(i + j) < '0' || text.charAt(i + j) > '9') {
                        isKeyno = false;
                    }
                    ++j;
                }
                if (text.charAt(i + 4) < 'A' || text.charAt(i + 4) > 'Z') {
                    isKeyno = false;
                }
                if (!(text.charAt(i + 5) >= 'A' && text.charAt(i + 5) <= 'Z' || text.charAt(i + 5) >= 'a' && text.charAt(i + 5) <= 'z')) {
                    isKeyno = false;
                }
                j = 6;
                while (j <= 7) {
                    if (!(text.charAt(i + j) >= '0' && text.charAt(i + j) <= '9' || text.charAt(i + j) >= 'A' && text.charAt(i + j) <= 'Z')) {
                        isKeyno = false;
                    }
                    ++j;
                }
            }
            if (i < text.length() - 6 && !isKeyno) {
                isKeyno = true;
                keynoLength = 5;
                j = 0;
                while (j <= 1) {
                    if (text.charAt(i + j) < '0' || text.charAt(i + j) > '9') {
                        isKeyno = false;
                    }
                    ++j;
                }
                if (text.charAt(i + 2) < 'A' || text.charAt(i + 2) > 'Z') {
                    isKeyno = false;
                }
                if (!(text.charAt(i + 3) >= 'A' && text.charAt(i + 3) <= 'Z' || text.charAt(i + 3) >= 'a' && text.charAt(i + 3) <= 'z')) {
                    isKeyno = false;
                }
                j = 4;
                while (j <= 5) {
                    if (!(text.charAt(i + j) >= '0' && text.charAt(i + j) <= '9' || text.charAt(i + j) >= 'A' && text.charAt(i + j) <= 'Z')) {
                        isKeyno = false;
                    }
                    ++j;
                }
            } else if (i >= text.length() - 6) {
                out = String.valueOf(out) + text.substring(i);
                break;
            }
            if (isKeyno) {
                i += keynoLength;
            } else {
                out = String.valueOf(out) + text.charAt(i);
            }
            ++i;
        }
        return out;
    }

    public static String exponential(String text) {
        int x = text.indexOf("E");
        String out = text;
        String base = "";
        String expo = "";
        if (x > 0 && x < text.length() - 1) {
            if (text.charAt(x + 1) != '+' && text.charAt(x + 1) != '-' && (text.charAt(x + 1) < '0' || text.charAt(x + 1) > '9')) {
                return text;
            }
            base = text.substring(0, x).replace("-", "$-$");
            expo = text.substring(x + 1);
            if (expo.charAt(0) == '+') {
                expo = expo.substring(1);
            }
            while (expo.length() > 0 && expo.charAt(0) == '0') {
                expo = expo.substring(1);
            }
            out = expo.length() > 0 ? String.valueOf(base) + "\\ensuremath{\\times10^{" + expo + "}}" : base;
        }
        if ((out = out.trim()).length() > 0 && out.charAt(0) == '-') {
            out = "$-$" + out.substring(1);
        }
        return out;
    }

    public static String getTransNuc(String s) {
        boolean foundSomething = false;
        String output = "";
        String text = s;
        output = UserDictionary.getTranslation(s);
        if (output.trim().length() > 0) {
            return output;
        }
        output = "";
        int x = 0;
        while (x < Nkey.length) {
            if (text.equals(Nkey[x].toUpperCase())) {
                return Nvalue[x];
            }
            ++x;
        }
        if (!foundSomething) {
            return " ";
        }
        return output;
    }

    public static String getTransRxn(String text) {
        int ch = 0;
        boolean mat = false;
        boolean foundSomething = false;
        boolean canBeTranslated = false;
        String output = "";
        output = UserDictionary.getTranslation(text);
        if (output.trim().length() > 0) {
            return output;
        }
        output = "";
        int imat = -1;
        while (ch < text.length()) {
            mat = false;
            imat = -1;
            int x = 0;
            while (x < Rkey.length) {
                if (Translator.match(Rkey[x], text, ch)) {
                    imat = ch;
                    output = String.valueOf(output) + Rvalue[x];
                    ch += Rkey[x].length();
                    mat = true;
                    foundSomething = true;
                    break;
                }
                ++x;
            }
            canBeTranslated = foundSomething && imat == 0;
            if (mat) continue;
            output = String.valueOf(output) + text.charAt(ch);
            ++ch;
        }
        if (!foundSomething) {
            return " ";
        }
        if (!canBeTranslated) {
            return " ";
        }
        return output;
    }

    public static String getTransWord(String s) {
        boolean foundSomething = false;
        String output = "";
        String text = s;
        output = UserDictionary.getTranslation(s);
        if (output.trim().length() > 0) {
            return output;
        }
        output = "";
        int x = 0;
        while (x < Wkey.length) {
            if (text.equals(Wkey[x].toUpperCase())) {
                return Wvalue[x];
            }
            ++x;
        }
        if (!foundSomething) {
            return " ";
        }
        return output;
    }

    public static void printKey() {
        int i = 0;
        while (i < Wkey.length) {
            System.out.println(String.valueOf(Wkey[i]) + " " + Wkey[i].length());
            ++i;
        }
    }

    public static String procGenCom_test(String s, int start, boolean cap) throws Exception {
        try {
            String s1 = Translator.procGenCom(s, start, cap);
            System.out.println("@@@@ s=" + s + " start=" + start + " cap=" + cap);
            return s1;
        }
        catch (StackOverflowError e) {
            System.out.println("####" + s + " start=" + start + " cap=" + cap);
            return " ";
        }
    }

    /*
     * Unable to fully structure code
     */
    public static String procGenCom(String s, int start, boolean cap) throws Exception {
        block118: {
            block119: {
                block120: {
                    if (start >= s.length()) {
                        return "";
                    }
                    text = s.substring(start);
                    out = "";
                    translated = "";
                    if (text.length() < 1) {
                        return "";
                    }
                    if (text.length() > 1000) {
                        sv = Translator.splitInHalf(text);
                        return String.valueOf(Translator.procGenCom(sv.get(0), 0, cap)) + Translator.procGenCom(sv.get(1), 0, false);
                    }
                    if (!UserDictionary.isEmpty() && text.charAt(0) != ' ' && !(translated = UserDictionary.getTranslation(temp = Translator.getFirstWordBySpace(text))).equals(" ")) {
                        return String.valueOf(translated) + Translator.procGenCom(text, temp.length(), false);
                    }
                    one = new Word(text, 0);
                    if (one.length() >= 1) break block118;
                    if (one.getDelim() == '.') {
                        return String.valueOf(one.getDelim()) + " " + Translator.procGenCom(text, 2, true);
                    }
                    if (one.getDelim() != '^') break block119;
                    i = 1;
                    if (text.length() <= 1) break block120;
                    if (text.charAt(i) != '*') ** GOTO lbl-1000
                    ++i;
                    break block120;
                    while (++i != text.length()) lbl-1000:
                    // 2 sources

                    {
                        if (text.charAt(i) != ' ' && Character.isLetter(text.charAt(i))) continue;
                    }
                }
                out = String.valueOf(out) + text.substring(1, i) + Translator.procGenCom(text, i, false);
                return out;
            }
            if (one.getDelim() == '|') {
                out = text.indexOf(124) == text.length() - 1 ? String.valueOf(out) + "|" : String.valueOf(out) + text.substring(0, 2) + Translator.procGenCom(text, 2, false);
                return out;
            }
            if (one.getDelim() == '}') {
                preWord = s.substring(0, start);
                one = new Word(text, 1);
                skip = true;
                if (one.getWord().length() > 0 && Str.isNumeric(preWord) && !(temp = Translator.getTransNuc(String.valueOf(preWord) + one.getWord().toUpperCase())).equals(" ")) {
                    skip = false;
                }
                if (skip) {
                    temp = Translator.getTransWord(one.getWord());
                    if (temp.equals(" ")) {
                        temp = one.getWord();
                    }
                    out = String.valueOf(out) + text.substring(0, 1) + temp + Translator.procGenCom(text, one.length() + 1, false);
                } else {
                    temp = String.valueOf(one.getWord().substring(0, 1)) + one.getWord().substring(1).toLowerCase();
                    out = String.valueOf(out) + text.substring(0, 1) + temp + Translator.procGenCom(text, one.length() + 1, false);
                }
                return out;
            }
            if (one.getDelim() == ' ') {
                next = new Word(text.substring(1).trim(), 0);
                lastWord = s.substring(0, start).trim();
                nextWord = next.getWord();
                if (Translator.isOperator(nextWord)) {
                    return Translator.procGenCom(text, text.indexOf(nextWord), false);
                }
                if (Translator.isOperator(lastWord)) {
                    return Translator.procGenCom(Str.ltrim(text), 0, false);
                }
                if (lastWord.equals("DELTA")) {
                    return Translator.procGenCom(Str.ltrim(text), 0, false);
                }
                if (text.trim().indexOf("\\\\ ") == 0) {
                    return Translator.procGenCom(Str.ltrim(text.replace("\\\\ ", "")), 0, false);
                }
                return String.valueOf(one.getDelim()) + Translator.procGenCom(text, 1, false);
            }
            if (one.getDelim() == '\'' || one.getDelim() == '%') {
                next = new Word(text, 1);
                nextWord = next.getWord();
                translated = Translator.getTransWord(String.valueOf(one.getDelim()) + nextWord);
                if (!translated.equals(" ")) {
                    return String.valueOf(translated) + Translator.procGenCom(text, nextWord.length() + 1, false);
                }
                return String.valueOf(one.getDelim()) + Translator.procGenCom(text, 1, false);
            }
            if (one.getDelim() == '<') {
                p = text.indexOf(62);
                if (p > 1 && p < 20 && !(translated = Translator.getTransWord(temp = text.substring(0, p + 1))).equals(" ")) {
                    return String.valueOf(translated) + Translator.procGenCom(text, temp.length(), false);
                }
                return String.valueOf(one.getDelim()) + Translator.procGenCom(text, 1, false);
            }
            if (one.getDelim() == '/' && start < text.length() && text.substring(start).startsWith("MN")) {
                return String.valueOf(one.getDelim()) + "MN" + Translator.procGenCom(text, 3, false);
            }
            return String.valueOf(one.getDelim()) + Translator.procGenCom(text, 1, false);
        }
        if (one.length() == 1) {
            temp = one.getWord();
            if (one.getDelim() == '.') {
                if (temp.equals("A") || temp.equals("B") || temp.equals("G")) {
                    return String.valueOf(temp) + Translator.procGenCom(text, 1, false);
                }
            } else if (one.getDelim() == ' ' && (temp.equals("P") || temp.equals("N")) && (nextWord = text.substring(1)).trim().startsWith("DECAY")) {
                n = text.indexOf("DECAY");
                translated = Translator.getTransWord(String.valueOf(temp) + " DECAY");
                if (!translated.equals(" ")) {
                    return String.valueOf(translated) + Translator.procGenCom(text, n + 5, false);
                }
            }
        }
        if ((text = text.trim()).indexOf("CONF=") == 0 && text.substring(5).trim().indexOf("(") == 0) {
            nbracket = 0;
            i = text.indexOf("(");
            conf = "";
            while (i < text.length()) {
                c = text.charAt(i);
                if (c == '(') {
                    ++nbracket;
                }
                if (c == ')') {
                    --nbracket;
                }
                conf = String.valueOf(conf) + c;
                if (nbracket == 0) {
                    try {
                        c0 = text.substring(i + 1).trim().charAt(0);
                        if (c0 != '(') {
                        }
                    }
                    catch (Exception e) {}
                    break;
                }
                ++i;
            }
            out = String.valueOf(out) + "Configuration=" + Translator.procCONF(conf) + Translator.procGenCom(text, i + 1, false);
            return out;
        }
        if (text.charAt(0) >= '0' && text.charAt(0) <= '9' && (n = new numanl(text, 0)).getLength() > 0) {
            out = String.valueOf(out) + n.getLine();
            out = String.valueOf(out) + Translator.procGenCom(text, n.getLength() + n.getNExtraSpaces(), false);
            return out;
        }
        if (text.charAt(0) == '(') {
            nBrackets = 1;
            size = 0;
            inBrackets = "";
            i = 1;
            while (i < text.length()) {
                if (text.charAt(i) == '(') {
                    ++nBrackets;
                } else if (text.charAt(i) == ')') {
                    --nBrackets;
                }
                if (nBrackets <= 0) {
                    inBrackets = text.substring(1, i);
                    size = i;
                    break;
                }
                if (i == text.length() - 1) {
                    size = i;
                    inBrackets = text.substring(1);
                }
                ++i;
            }
            sb = "(" + inBrackets + ")";
            translated = Translator.getTransRxn(sb.replace("^", ""));
            if (!translated.equals(" ")) {
                out = String.valueOf(out) + translated + Translator.procGenCom(text, size + 1, false);
            } else {
                translated = Translator.getTransWord("(" + inBrackets + ")");
                if (!translated.equals(" ")) {
                    out = String.valueOf(out) + translated + Translator.procGenCom(text, size + 1, false);
                } else if (size > 0) {
                    parts = inBrackets.split("[,]");
                    if (parts != null && parts.length == 2 && parts[0].trim().length() > 0 && parts[1].trim().length() > 1 && text.contains(")")) {
                        couldBeDSID = true;
                        s1 = parts[0].trim();
                        s2 = parts[1].trim();
                        n1 = s1.length();
                        if (s2.length() > 10 || !Str.isLettersOrDigits(s2) || !Str.isLettersOrDigits(s1)) {
                            couldBeDSID = false;
                        } else if (n1 == 1) {
                            if (!"ADPNTG".contains(s1 = s1.toUpperCase())) {
                                couldBeDSID = false;
                            }
                        } else if (s1.charAt(0) >= '1' && s1.charAt(0) <= '9' && ((nuc = new Nucleus(s1)).Z() <= 0 || nuc.A() <= 0)) {
                            couldBeDSID = false;
                        }
                        if (couldBeDSID) {
                            tempV = new Vector<String>();
                            i = 0;
                            letterCount = 0;
                            partS = "";
                            prevChar = '\u0000';
                            while (i < s2.length()) {
                                c = s2.charAt(i);
                                if (Character.isDigit(c)) {
                                    if (!Character.isDigit(prevChar) && partS.length() > 0) {
                                        tempV.add(partS);
                                        partS = "";
                                        letterCount = 0;
                                    }
                                } else {
                                    if (Str.isLetters(partS)) {
                                        tempV.add(partS);
                                        partS = "";
                                        letterCount = 0;
                                    } else if (!Str.isDigits(partS) && partS.length() > 0) {
                                        if (letterCount >= 2) {
                                            tempV.add(partS);
                                            partS = "";
                                            letterCount = 0;
                                        } else {
                                            if (Nucleus.isNucleus(String.valueOf(partS) + c)) {
                                                tempV.add(String.valueOf(partS) + c);
                                                partS = "";
                                                letterCount = 0;
                                                prevChar = c;
                                                ++i;
                                                continue;
                                            }
                                            tempV.add(partS);
                                            partS = "";
                                            letterCount = 0;
                                        }
                                    }
                                    ++letterCount;
                                }
                                partS = String.valueOf(partS) + c;
                                prevChar = c;
                                ++i;
                            }
                            if (partS.length() > 0) {
                                tempV.add(partS);
                            }
                            tempTranslated = "";
                            for (String ps : tempV) {
                                tempS = "";
                                tempS = ps.length() == 1 && "PDTN".contains(ps) != false ? ps.toLowerCase() : (ps.length() > 0 ? Translator.procGenCom(ps, 0, false) : ps);
                                if (tempS.trim().length() > 0) {
                                    tempTranslated = String.valueOf(tempTranslated) + tempS;
                                    continue;
                                }
                                tempTranslated = "";
                                break;
                            }
                            if (tempTranslated.length() > 0 && parts.length > 0 && parts[0].length() > 0) {
                                translated = tempTranslated;
                                Translator.canBeTranslatedAsDSID = true;
                                out = String.valueOf(out) + "(" + Translator.procGenCom(parts[0], 0, false) + "," + tempTranslated + Translator.procGenCom(text, inBrackets.length() + 1, false);
                            }
                        }
                    }
                    if (translated.equals(" ")) {
                        Translator.canBeTranslatedAsDSID = false;
                        out = String.valueOf(out) + "(" + Translator.procGenCom(text, 1, false);
                    }
                } else {
                    out = String.valueOf(out) + Translator.procGenCom(text, size + 1, false);
                }
            }
        } else if (text.charAt(0) != '^' && text.charAt(0) != '|') {
            w1 = new Word(text, 0);
            c = w1.getDelim();
            temp = w1.getWord();
            endOfLastWord = w1.length();
            ntries = 3;
            goodTranslation = "";
            goodLastWord = temp;
            goodLastDelim = c;
            goodEndOfLastWord = endOfLastWord;
            matchFound = false;
            nMatches = 0;
            if (c != ' ' && c != '$' && c != 'n' && c != '!' && endOfLastWord < text.length()) {
                i = 0;
                while (i < ntries) {
                    matchFound = false;
                    if (endOfLastWord > text.length() - 1) break;
                    if (c == '(') {
                        nextWord = new Word(text, endOfLastWord);
                        temp = String.valueOf(temp) + nextWord.getWord();
                        c = nextWord.getDelim();
                        endOfLastWord += nextWord.length();
                    } else {
                        nextWord = new Word(text, endOfLastWord + 1);
                        temp = String.valueOf(temp) + c + nextWord.getWord();
                        endOfLastWord += 1 + nextWord.length();
                        c = nextWord.getDelim();
                    }
                    translated = Translator.getTransWord(temp);
                    if (!translated.equals(" ")) {
                        goodTranslation = translated;
                        goodEndOfLastWord = endOfLastWord;
                        matchFound = true;
                        ++nMatches;
                    }
                    if (c != '!' && c != ' ' && c != 'n' && !(translated = Translator.getTransWord(String.valueOf(temp) + c)).equals(" ")) {
                        goodTranslation = translated;
                        goodEndOfLastWord = endOfLastWord + 1;
                        temp = String.valueOf(temp) + c;
                        matchFound = true;
                        ++nMatches;
                    }
                    if (matchFound) {
                        if (c != '!' && c != ' ' && c != 'n') {
                            goodLastDelim = c;
                        }
                        goodLastWord = temp;
                    }
                    ++i;
                }
                if (nMatches == 0) {
                    temp = w1.getWord();
                    c = w1.getDelim();
                    endOfLastWord = w1.length();
                } else {
                    temp = goodLastWord;
                    c = goodLastDelim;
                    endOfLastWord = goodEndOfLastWord;
                }
            }
            if (nMatches == 0) {
                translated = Translator.getTransWord(temp);
                translatedNuc = Translator.getTransNuc(temp);
                isNuc = false;
                if (temp.length() <= 2 && translated.equals(" ") && !translatedNuc.equals(" ") && c == '(') {
                    translated = translatedNuc;
                    isNuc = true;
                }
                if (!isNuc) {
                    tempTranslated = Translator.getTransWord(String.valueOf(temp) + c);
                    if (!tempTranslated.equals(" ")) {
                        isSetOriginal = false;
                        if (!(translated.equals(" ") || c != '+' || (ns = (nextWord = new Word(text, endOfLastWord + 1)).getWord().trim()).isEmpty() || Translator.getTransWord(ns).equals(" "))) {
                            isSetOriginal = true;
                        }
                        if (!isSetOriginal) {
                            translated = tempTranslated;
                            ++endOfLastWord;
                        }
                    } else if (translated.equals(" ")) {
                        translated = Translator.getTransRxn(temp);
                        if (translated.equals(" ")) {
                            translated = translatedNuc;
                        }
                        if (translated.equals(" ") && c == ')') {
                            s1 = " ";
                            s2 = " ";
                            i = 0;
                            while (i < temp.length()) {
                                if (Character.isDigit(temp.charAt(i)) && i > 0) {
                                    s1 = temp.substring(0, i);
                                    s2 = temp.substring(i);
                                    break;
                                }
                                ++i;
                            }
                            s1 = Translator.getTransWord(s1);
                            s2 = Translator.getTransNuc(s2);
                            if (!s1.equals(" ") && !s2.equals(" ")) {
                                translated = String.valueOf(s1) + s2;
                            }
                        }
                        if (translated.equals(" ")) {
                            allChars = true;
                            i = 0;
                            while (i < temp.length()) {
                                if (temp.charAt(i) < 'A' || temp.charAt(i) > 'Z') {
                                    allChars = false;
                                }
                                ++i;
                            }
                            inDSIDBrackets = false;
                            if (allChars && temp.length() > 0) {
                                ch = temp.charAt(temp.length() - 1);
                                if (c == ')' && ch == 'X' && s.charAt(0) != '(') {
                                    inDSIDBrackets = true;
                                    if (temp.equals("AX")) {
                                        temp = "|aX";
                                    } else if (temp.length() > 1) {
                                        temp = String.valueOf(temp.substring(0, temp.length() - 1).toLowerCase()) + 'X';
                                    }
                                } else {
                                    temp = temp.toLowerCase();
                                }
                            }
                            if (cap && !inDSIDBrackets) {
                                if (allChars) {
                                    temp = String.valueOf(temp.substring(0, 1).toUpperCase()) + temp.substring(1);
                                } else if (Str.isUpperCase(text.charAt(0)) && text.toLowerCase().charAt(0) == temp.charAt(0)) {
                                    temp = String.valueOf(temp.substring(0, 1).toUpperCase()) + temp.substring(1);
                                }
                            }
                            translated = temp;
                        }
                    }
                }
            } else {
                translated = goodTranslation;
            }
            if (translated.equals("@@@")) {
                if (endOfLastWord + 1 < text.length()) {
                    w3 = new Word(text, endOfLastWord + 1);
                    two = String.valueOf(temp) + c + w3.getWord();
                    translated = Translator.getTransWord(two);
                    if (!translated.equals(" ")) {
                        endOfLastWord += w3.length() + 1;
                    } else {
                        temp = cap != false ? String.valueOf(temp.substring(0, 1).toUpperCase()) + temp.substring(1) : temp.toLowerCase();
                        translated = temp;
                    }
                } else {
                    translated = temp;
                }
            }
            out = String.valueOf(out) + translated;
            out = String.valueOf(out) + Translator.procGenCom(text, endOfLastWord, false);
        }
        return out;
    }

    public static String procGenCom2(String s, int start, boolean cap) {
        String out = "";
        String text = s.substring(start);
        boolean c = cap;
        String firstWord = "";
        String prevWord = s.substring(0, start);
        while (text.length() > 0) {
            String[] results = Translator.procGenComFirstWord(text, prevWord, c);
            firstWord = results[0];
            out = String.valueOf(out) + firstWord;
            c = false;
            if (firstWord.equals(". ")) {
                c = true;
            }
            prevWord = results[1];
            text = results[2];
        }
        return out;
    }

    private static String[] makeArray(String translated, String firstWord, String restText) {
        String[] out = new String[]{translated, firstWord, restText};
        return out;
    }

    private static String[] makeEmptyArray() {
        return Translator.makeArray("", "", "");
    }

    /*
     * Unable to fully structure code
     */
    public static String[] procGenComFirstWord(String s, String prevWord, boolean cap) {
        block107: {
            block108: {
                block109: {
                    text = s.substring(0);
                    translated = "";
                    firstWord = "";
                    temp = "";
                    if (text.length() < 1) {
                        return Translator.makeEmptyArray();
                    }
                    if (!UserDictionary.isEmpty() && text.charAt(0) != ' ' && !(translated = UserDictionary.getTranslation(temp = Translator.getFirstWordBySpace(text))).equals(" ")) {
                        text = text.substring(temp.length());
                        return Translator.makeArray(translated, temp, text);
                    }
                    one = new Word(text, 0);
                    if (one.length() >= 1) break block107;
                    if (one.getDelim() == '.') {
                        if (text.length() > 2) {
                            text = text.substring(2);
                            temp = String.valueOf(one.getDelim()) + " ";
                            return Translator.makeArray(temp, temp, text);
                        }
                        text = "";
                        temp = String.valueOf(one.getDelim());
                        return Translator.makeArray(temp, temp, text);
                    }
                    if (one.getDelim() != '^') break block108;
                    i = 1;
                    if (text.length() <= 1) break block109;
                    if (text.charAt(i) != '*') ** GOTO lbl-1000
                    ++i;
                    break block109;
                    while (++i != text.length()) lbl-1000:
                    // 2 sources

                    {
                        if (text.charAt(i) != ' ' && Character.isLetter(text.charAt(i))) continue;
                    }
                }
                translated = String.valueOf(translated) + text.substring(1, i);
                text = text.substring(i);
                return Translator.makeArray(translated, translated, text);
            }
            if (one.getDelim() == '|') {
                if (text.indexOf(124) == text.length() - 1) {
                    translated = String.valueOf(translated) + "|";
                } else if (text.length() > 2) {
                    translated = String.valueOf(translated) + text.substring(0, 2);
                    text = text.substring(2);
                } else {
                    translated = String.valueOf(translated) + text;
                    text = "";
                }
                return Translator.makeArray(translated, translated, text);
            }
            if (one.getDelim() == '}') {
                one = new Word(text, 1);
                skip = true;
                if (one.getWord().length() > 0 && Str.isNumeric(prevWord) && !(temp = Translator.getTransNuc(String.valueOf(prevWord) + one.getWord().toUpperCase())).equals(" ")) {
                    skip = false;
                }
                if (skip) {
                    temp = Translator.getTransWord(one.getWord());
                    if (temp.equals(" ")) {
                        temp = one.getWord();
                    }
                    translated = String.valueOf(translated) + text.substring(0, 1) + temp;
                    if (text.length() > one.length() + 1) {
                        firstWord = text.substring(0, one.length() + 1);
                        text = text.substring(one.length() + 1);
                    } else {
                        firstWord = text;
                        text = "";
                    }
                } else {
                    temp = String.valueOf(one.getWord().substring(0, 1)) + one.getWord().substring(1).toLowerCase();
                    if (text.length() > 1) {
                        translated = String.valueOf(translated) + text.substring(0, 1) + temp;
                        text = text.substring(one.length() + 1);
                        firstWord = text.substring(0, one.length() + 1);
                    } else {
                        translated = String.valueOf(translated) + text;
                        text = "";
                        firstWord = translated;
                    }
                }
                return Translator.makeArray(translated, firstWord, text);
            }
            if (one.getDelim() == ' ') {
                next = new Word(text.substring(1).trim(), 0);
                nextWord = next.getWord();
                if (Translator.isOperator(nextWord)) {
                    text = text.substring(text.indexOf(nextWord));
                    return Translator.makeArray("", "", text);
                }
                if (Translator.isOperator(prevWord)) {
                    text = Str.ltrim(text);
                    return Translator.makeArray("", "", text);
                }
                if (prevWord.equals("DELTA")) {
                    text = Str.ltrim(text);
                    return Translator.makeArray("", "", text);
                }
                if (text.trim().indexOf("\\\\ ") == 0) {
                    text = Str.ltrim(text.replace("\\\\ ", ""));
                    return Translator.makeArray("", "", text);
                }
                text = text.length() > 1 ? text.substring(1) : "";
                temp = String.valueOf(one.getDelim());
                return Translator.makeArray(temp, temp, text);
            }
            if (one.getDelim() == '\'' || one.getDelim() == '%') {
                next = new Word(text.substring(1).trim(), 0);
                nextWord = next.getWord();
                translated = Translator.getTransWord(String.valueOf(one.getDelim()) + nextWord);
                if (!translated.equals(" ")) {
                    firstWord = text.substring(0, nextWord.length() + 1);
                    text = text.substring(nextWord.length() + 1);
                } else {
                    firstWord = String.valueOf(one.getDelim());
                    text = text.substring(1);
                }
                return Translator.makeArray(translated, firstWord, text);
            }
            if (one.getDelim() == '<') {
                p = text.indexOf(62);
                if (p > 1 && p < 20) {
                    temp = text.substring(0, p + 1);
                    translated = Translator.getTransWord(temp);
                    if (!translated.equals(" ")) {
                        firstWord = temp;
                        text = text.substring(p + 1);
                    } else {
                        firstWord = String.valueOf(one.getDelim());
                        text = text.substring(1);
                    }
                }
                return Translator.makeArray(translated, firstWord, text);
            }
            text = text.length() > 1 ? text.substring(1) : "";
            temp = String.valueOf(one.getDelim());
            return Translator.makeArray(temp, temp, text);
        }
        if (one.length() == 1) {
            temp = one.getWord();
            if (one.getDelim() == '.') {
                if (temp.equals("A") || temp.equals("B") || temp.equals("G")) {
                    return Translator.makeArray(temp, temp, text.substring(1));
                }
            } else if (one.getDelim() == ' ' && (temp.equals("P") || temp.equals("N")) && (nextWord = text.substring(1)).trim().startsWith("DECAY")) {
                n = text.indexOf("DECAY");
                temp = text.substring(0, n + 5);
                translated = Translator.getTransWord(String.valueOf(temp) + " DECAY");
                return Translator.makeArray(temp, translated, text.substring(n + 5));
            }
        }
        if ((text = text.trim()).length() <= 0) {
            return Translator.makeEmptyArray();
        }
        if (text.indexOf("CONF=") == 0 && text.substring(5).trim().indexOf("(") == 0) {
            nbracket = 0;
            i = text.indexOf("(");
            conf = "";
            while (i < text.length()) {
                c = text.charAt(i);
                if (c == '(') {
                    ++nbracket;
                }
                if (c == ')') {
                    --nbracket;
                }
                conf = String.valueOf(conf) + c;
                if (nbracket == 0) {
                    try {
                        c0 = text.substring(i + 1).trim().charAt(0);
                        if (c0 != '(') {
                        }
                    }
                    catch (Exception e) {}
                    break;
                }
                ++i;
            }
            if (i + 1 < text.length()) {
                firstWord = text.substring(0, i + 1);
                text = text.substring(i + 1);
            } else {
                firstWord = text;
                text = "";
            }
            translated = String.valueOf(translated) + "Configuration=" + Translator.procCONF2(conf);
            return Translator.makeArray(translated, firstWord, text);
        }
        if (text.charAt(0) >= '0' && text.charAt(0) <= '9' && (n = new numanl(text, 0)).getLength() > 0) {
            translated = String.valueOf(translated) + n.getLine();
            firstWord = text.substring(0, n.getLength() + n.getNExtraSpaces());
            text = text.substring(n.getLength() + n.getNExtraSpaces());
            return Translator.makeArray(translated, firstWord, text);
        }
        if (text.charAt(0) != '(') ** GOTO lbl205
        nBrackets = 1;
        size = 0;
        inBrackets = "";
        i = 1;
        while (i < text.length()) {
            if (text.charAt(i) == '(') {
                ++nBrackets;
            } else if (text.charAt(i) == ')') {
                --nBrackets;
            }
            if (nBrackets <= 0) {
                inBrackets = text.substring(1, i);
                size = i;
                break;
            }
            if (i == text.length() - 1) {
                size = i;
                inBrackets = text.substring(1);
            }
            ++i;
        }
        translated = Translator.getTransRxn("(" + inBrackets + ")");
        if (!translated.equals(" ")) {
            firstWord = text.substring(0, size + 1);
            text = text.substring(size + 1);
        } else {
            translated = Translator.getTransWord("(" + inBrackets + ")");
            if (!translated.equals(" ")) {
                firstWord = text.substring(0, size + 1);
                text = text.substring(size + 1);
            } else if (size > 0) {
                if (inBrackets.length() >= 6 && inBrackets.length() <= 8 && inBrackets.charAt(0) >= '1') {
                    inBrackets.charAt(0);
                }
                text = text.substring(1);
                translated = "(";
                firstWord = "(";
                Translator.canBeTranslatedAsDSID = false;
            } else {
                firstWord = text.substring(0, size + 1);
                text = text.substring(size + 1);
            }
            return Translator.makeArray(translated, firstWord, text);
lbl205:
            // 1 sources

            if (text.charAt(0) != '^' && text.charAt(0) != '|') {
                w1 = new Word(text, 0);
                c = w1.getDelim();
                temp = w1.getWord();
                endOfLastWord = w1.length();
                ntries = 3;
                goodTranslation = "";
                goodLastWord = temp;
                goodLastDelim = c;
                goodEndOfLastWord = endOfLastWord;
                matchFound = false;
                nMatches = 0;
                if (c != ' ' && c != '$' && c != 'n' && endOfLastWord < text.length()) {
                    i = 0;
                    while (i < ntries) {
                        matchFound = false;
                        if (endOfLastWord > text.length() - 1) break;
                        if (c == '(') {
                            nextWord = new Word(text, endOfLastWord);
                            temp = String.valueOf(temp) + nextWord.getWord();
                            c = nextWord.getDelim();
                            endOfLastWord += nextWord.length();
                        } else {
                            nextWord = new Word(text, endOfLastWord + 1);
                            temp = String.valueOf(temp) + c + nextWord.getWord();
                            c = nextWord.getDelim();
                            endOfLastWord += nextWord.length() + 1;
                        }
                        translated = Translator.getTransWord(temp);
                        if (!translated.equals(" ")) {
                            goodTranslation = translated;
                            goodEndOfLastWord = endOfLastWord + 1;
                            matchFound = true;
                            ++nMatches;
                        }
                        if (c != '!' && c != ' ' && c != 'n' && !(translated = Translator.getTransWord(String.valueOf(temp) + c)).equals(" ")) {
                            goodTranslation = translated;
                            goodEndOfLastWord = endOfLastWord + 1;
                            temp = String.valueOf(temp) + c;
                            matchFound = true;
                            ++nMatches;
                        }
                        if (matchFound) {
                            if (c != '!' && c != ' ' && c != 'n') {
                                goodLastDelim = c;
                            }
                            goodLastWord = temp;
                        }
                        ++i;
                    }
                    if (nMatches == 0) {
                        temp = w1.getWord();
                        c = w1.getDelim();
                        endOfLastWord = w1.length();
                    } else {
                        temp = goodLastWord;
                        c = goodLastDelim;
                        endOfLastWord = goodEndOfLastWord;
                    }
                }
                if (nMatches == 0) {
                    translated = Translator.getTransWord(temp);
                    translatedNuc = Translator.getTransNuc(temp);
                    isNuc = false;
                    if (temp.length() <= 2 && !translated.equals(" ") && !translatedNuc.equals(" ") && c == '(') {
                        translated = translatedNuc;
                        isNuc = true;
                    }
                    if (!isNuc) {
                        tempTranslated = Translator.getTransWord(String.valueOf(temp) + c);
                        if (!translatedNuc.equals(" ")) {
                            isSetOriginal = false;
                            if (!(translated.equals(" ") || c != '+' || (ns = (nextWord = new Word(text, endOfLastWord + 1)).getWord().trim()).isEmpty() || Translator.getTransWord(ns).equals(" "))) {
                                isSetOriginal = true;
                            }
                            if (!isSetOriginal) {
                                translated = tempTranslated;
                                ++endOfLastWord;
                            }
                        } else if (translated.equals(" ")) {
                            translated = Translator.getTransRxn(temp);
                            if (translated.equals(" ")) {
                                translated = translatedNuc;
                            }
                            if (translated.equals(" ") && c == ')') {
                                s1 = " ";
                                s2 = " ";
                                i = 0;
                                while (i < temp.length()) {
                                    if (Character.isDigit(temp.charAt(i)) && i > 0) {
                                        s1 = temp.substring(0, i);
                                        s2 = temp.substring(i);
                                        break;
                                    }
                                    ++i;
                                }
                                s1 = Translator.getTransWord(s1);
                                s2 = Translator.getTransNuc(s2);
                                if (!s1.equals(" ") && !s2.equals(" ")) {
                                    translated = String.valueOf(s1) + s2;
                                }
                            }
                            if (translated.equals(" ")) {
                                allChars = true;
                                i = 0;
                                while (i < temp.length()) {
                                    if (temp.charAt(i) < 'A' || temp.charAt(i) > 'Z') {
                                        allChars = false;
                                    }
                                    ++i;
                                }
                                inDSIDBrackets = false;
                                if (allChars && temp.length() > 0) {
                                    ch = temp.charAt(temp.length() - 1);
                                    if (c == ')' && ch == 'X' && s.charAt(0) != '(') {
                                        inDSIDBrackets = true;
                                        if (temp.equals("AX")) {
                                            temp = "|aX";
                                        } else if (temp.length() > 1) {
                                            temp = String.valueOf(temp.substring(0, temp.length() - 1).toLowerCase()) + 'X';
                                        }
                                    } else {
                                        temp = temp.toLowerCase();
                                    }
                                }
                                if (cap && !inDSIDBrackets) {
                                    if (allChars) {
                                        temp = String.valueOf(temp.substring(0, 1).toUpperCase()) + temp.substring(1);
                                    } else if (Str.isUpperCase(text.charAt(0)) && text.toLowerCase().charAt(0) == temp.charAt(0)) {
                                        temp = String.valueOf(temp.substring(0, 1).toUpperCase()) + temp.substring(1);
                                    }
                                }
                                translated = temp;
                            }
                        }
                    }
                } else {
                    translated = goodTranslation;
                }
                if (translated.equals("@@@")) {
                    if (endOfLastWord + 1 < text.length()) {
                        w3 = new Word(text, endOfLastWord + 1);
                        two = String.valueOf(temp) + c + w3.getWord();
                        translated = Translator.getTransWord(two);
                        if (!translated.equals(" ")) {
                            endOfLastWord += w3.length() + 1;
                        } else {
                            temp = cap != false ? String.valueOf(temp.substring(0, 1).toUpperCase()) + temp.substring(1) : temp.toLowerCase();
                            translated = temp;
                        }
                    } else {
                        translated = temp;
                    }
                }
                if (endOfLastWord < text.length()) {
                    firstWord = text.substring(0, endOfLastWord);
                    text = text.substring(endOfLastWord);
                } else {
                    firstWord = text;
                    text = "";
                }
            }
        }
        return Translator.makeArray(translated, firstWord, text);
    }

    public static String JPI(String jpi) {
        if (jpi.contains(",")) {
            jpi = jpi.substring(0, jpi.indexOf(","));
        }
        if (jpi.contains(":")) {
            jpi = jpi.substring(0, jpi.indexOf(":"));
        }
        if (jpi.startsWith("(") && !jpi.contains(")")) {
            jpi = String.valueOf(jpi) + ")";
        }
        return jpi;
    }

    public static String procCONF2(String confS) {
        boolean hasType = false;
        boolean hasOrbital = false;
        String out = "";
        String conf = confS;
        if (!conf.contains("(") && !conf.contains(")")) {
            return conf;
        }
        int i = 0;
        int ispace = -1;
        while (i < conf.length()) {
            char c = conf.charAt(i);
            if (c == ')') {
                hasType = false;
                hasOrbital = false;
            } else {
                String s1;
                if (!(c != 'P' && c != 'p' || hasType)) {
                    out = String.valueOf(out) + "|p";
                    hasType = true;
                    if (++i < conf.length() && conf.toUpperCase().charAt(i) == 'I') {
                        ++i;
                    }
                    if (i >= conf.length() || conf.charAt(i) != ' ' || (s1 = conf.substring(i).trim()).charAt(0) == ',') continue;
                    conf = String.valueOf(conf.substring(0, i)) + ',' + conf.substring(i + 1);
                    continue;
                }
                if (!(c != 'N' && c != 'n' || hasType)) {
                    out = String.valueOf(out) + "|n";
                    hasType = true;
                    if (++i < conf.length() && conf.toUpperCase().charAt(i) == 'U') {
                        ++i;
                    }
                    if (i >= conf.length() || conf.charAt(i) != ' ' || (s1 = conf.substring(i).trim()).charAt(0) == ',') continue;
                    conf = String.valueOf(conf.substring(0, i)) + ',' + conf.substring(i + 1);
                    continue;
                }
                if (c == ',') {
                    String s;
                    if (hasType && hasOrbital) {
                        ++i;
                        s = "";
                        while (i < conf.length()) {
                            c = conf.charAt(i);
                            if (Character.isDigit(c)) {
                                s = String.valueOf(s) + conf.charAt(i);
                            } else if (c != ' ') break;
                            ++i;
                        }
                        if (c == ')') {
                            hasType = false;
                            hasOrbital = false;
                            out = String.valueOf(out) + "){+" + s + "}";
                        }
                        ++i;
                        continue;
                    }
                    if (hasType && !hasOrbital) {
                        ++i;
                        s = "";
                        while (i < conf.length()) {
                            c = conf.charAt(i);
                            if (c == ' ' && s.trim().length() > 0 || c == ',' || c == ')') break;
                            s = String.valueOf(s) + c;
                            ++i;
                        }
                        if (s.trim().length() <= 0) continue;
                        out = String.valueOf(out) + " " + Translator.orbital(s.trim());
                        hasOrbital = true;
                        continue;
                    }
                    out = String.valueOf(out) + " ";
                    ++i;
                    continue;
                }
                if (c == ' ') {
                    if (!hasType && !hasOrbital && i > ispace + 1) {
                        out = String.valueOf(out) + " ";
                    }
                    ispace = i++;
                    continue;
                }
            }
            out = String.valueOf(out) + c;
            ++i;
        }
        return out;
    }

    public static String procCONF(String confS) throws Exception {
        boolean hasType = false;
        boolean hasOrbital = false;
        String out = "";
        String conf = confS;
        if (!conf.contains("(") && !conf.contains(")")) {
            return conf;
        }
        if (conf.contains("|n") || conf.contains("|p")) {
            return conf;
        }
        int i = 0;
        int ispace = -1;
        while (i < conf.length()) {
            char c = conf.charAt(i);
            if (c == ')') {
                hasType = false;
                hasOrbital = false;
            } else {
                String s1;
                if (!(c != 'P' && c != 'p' || hasType)) {
                    out = String.valueOf(out) + "|p";
                    hasType = true;
                    if (++i < conf.length() && conf.toUpperCase().charAt(i) == 'I') {
                        ++i;
                    }
                    if (i >= conf.length() || conf.charAt(i) != ' ' || (s1 = conf.substring(i).trim()).charAt(0) == ',') continue;
                    conf = String.valueOf(conf.substring(0, i)) + ',' + conf.substring(i + 1);
                    continue;
                }
                if (!(c != 'N' && c != 'n' || hasType)) {
                    out = String.valueOf(out) + "|n";
                    hasType = true;
                    if (++i < conf.length() && conf.toUpperCase().charAt(i) == 'U') {
                        ++i;
                    }
                    if (i >= conf.length() || conf.charAt(i) != ' ' || (s1 = conf.substring(i).trim()).charAt(0) == ',') continue;
                    conf = String.valueOf(conf.substring(0, i)) + ',' + conf.substring(i + 1);
                    continue;
                }
                if (c == ',') {
                    String s;
                    if (hasType && hasOrbital) {
                        ++i;
                        s = "";
                        while (i < conf.length()) {
                            c = conf.charAt(i);
                            if (Character.isDigit(c) || "+-".indexOf(c) >= 0) {
                                s = String.valueOf(s) + conf.charAt(i);
                            } else if (c != ' ') break;
                            ++i;
                        }
                        if (c == ')') {
                            hasType = false;
                            hasOrbital = false;
                            out = String.valueOf(out) + "){+" + s + "}";
                        }
                        ++i;
                        continue;
                    }
                    if (hasType && !hasOrbital) {
                        ++i;
                        s = "";
                        while (i < conf.length()) {
                            c = conf.charAt(i);
                            if (c == ' ' && s.trim().length() > 0 || c == ',' || c == ')') break;
                            s = String.valueOf(s) + c;
                            ++i;
                        }
                        if ((s = s.trim()).length() <= 0) continue;
                        if (s.indexOf("{") < 0) {
                            s = s.toUpperCase();
                        }
                        out = String.valueOf(out) + " " + Translator.procGenCom(s.trim(), 0, false);
                        hasOrbital = true;
                        continue;
                    }
                    out = String.valueOf(out) + " ";
                    ++i;
                    continue;
                }
                if (c == ' ') {
                    if (!hasType && !hasOrbital && i > ispace + 1) {
                        out = String.valueOf(out) + " ";
                    }
                    ispace = i++;
                    continue;
                }
            }
            out = String.valueOf(out) + c;
            ++i;
        }
        return out;
    }

    public static boolean isOperator(String s) {
        return operators.contains(s);
    }

    public static void test() throws Exception {
        String filename = "TranslatorTest.txt";
        FileOutputStream os = new FileOutputStream(filename);
        PrintStream ps = new PrintStream(os);
        System.setOut(ps);
        String match = "";
        String translation = "";
        int i = 0;
        while (i < Wkey.length) {
            match = "";
            translation = Translator.procGenCom(Wkey[i], 0, false);
            if (!translation.equals(Wvalue[i])) {
                match = "NO";
            }
            System.out.println(String.format("%20s %20s %20s %10s", Wkey[i], Wvalue[i], translation, match));
            ++i;
        }
    }

    public static boolean canBeTranslated(String s) {
        String output = UserDictionary.getTranslation(s);
        if (output.trim().length() > 0) {
            return true;
        }
        output = "";
        int i = 0;
        while (i < Rkey.length) {
            if (s.equals(Rkey[i])) {
                return true;
            }
            ++i;
        }
        i = 0;
        while (i < Nkey.length) {
            if (s.equals(Nkey[i])) {
                return true;
            }
            ++i;
        }
        i = 0;
        while (i < Wkey.length) {
            if (s.equals(Wkey[i])) {
                return true;
            }
            ++i;
        }
        return false;
    }

    public static String getFirstWordBySpace(String s) {
        String temp = s.trim();
        if (temp.length() <= 0) {
            return "";
        }
        int p = temp.indexOf(32);
        if (p <= 0) {
            return temp;
        }
        return temp.substring(0, p);
    }

    public static Vector<String> splitInHalf(String s) {
        Vector<String> out = new Vector<String>();
        int len = s.length();
        int p = 0;
        int middle = len / 2 - 1;
        int i = 0;
        while (i <= middle) {
            String s1;
            p = 0;
            if (s.charAt(middle - i) == ' ') {
                p = middle - i;
            } else if (s.charAt(middle + i) == ' ') {
                p = middle + i;
            }
            if (p > 0 && (s1 = s.substring(0, p)).lastIndexOf(123) <= s1.lastIndexOf(125) && s1.lastIndexOf(40) <= s1.lastIndexOf(41)) break;
            ++i;
        }
        if (Math.abs(len - 2 * p - 2) < len / 3) {
            out.add(s.substring(0, p));
            out.add(s.substring(p));
        } else {
            out.add(s);
        }
        return out;
    }

    public static boolean isPI(String text, int n) {
        int i;
        int len = text.length();
        if (len <= 0 || n > len - 1 || n <= 0) {
            return false;
        }
        char c = text.charAt(n);
        if (c != '+' && c != '-') {
            return false;
        }
        char[] delims = new char[]{' ', ',', '.', ';', ':', ')', ']', '+', '~'};
        char prevChar = '\u0000';
        char nextChar = '\u0000';
        boolean isPI = false;
        boolean isAfterNumbers = false;
        prevChar = text.charAt(n - 1);
        if (Character.isLetter(prevChar)) {
            isAfterNumbers = false;
        } else if (Character.isDigit(prevChar)) {
            isAfterNumbers = true;
        } else if (prevChar == ')') {
            if (n > 1 && Character.isDigit(text.charAt(n - 2))) {
                isAfterNumbers = true;
            }
        } else if (prevChar == '(') {
            if (n > 1) {
                if (Character.isDigit(text.charAt(n - 2))) {
                    isAfterNumbers = true;
                } else if (text.charAt(n - 2) == ')' && n > 2 && Character.isDigit(text.charAt(n - 3))) {
                    isAfterNumbers = true;
                }
            }
        } else {
            isAfterNumbers = false;
        }
        if (isAfterNumbers) {
            if (n == len - 1) {
                isPI = true;
            } else {
                nextChar = text.charAt(n + 1);
                if (nextChar == '(' && n < len - 2 && text.charAt(n + 2) == '+') {
                    isPI = true;
                } else if (nextChar == '[' && n < len - 2 && Character.isDigit(text.charAt(n + 2))) {
                    if (n < len - 5 && text.charAt(n + 5) == ']' && Str.isNumeric(text.substring(n + 2, n + 5))) {
                        isPI = true;
                    }
                } else if (!Character.isLetterOrDigit(nextChar)) {
                    i = 0;
                    while (i < delims.length) {
                        if (nextChar == delims[i]) {
                            isPI = true;
                            break;
                        }
                        ++i;
                    }
                }
            }
        }
        if (isPI && text.charAt(n) == '-' && Character.isDigit(prevChar)) {
            i = n - 2;
            while (i >= 0) {
                if (!Character.isDigit(text.charAt(i)) && text.charAt(i) != '.') break;
                --i;
            }
            int count = n - 1 - i;
            if (count >= 3) {
                isPI = false;
            } else {
                String prevPart = text.substring(0, i + 1);
                String tempS = prevPart.replace("=", "").replace("|>", "").replace("|<", "").replace("|?", "").trim();
                if (tempS.endsWith("J|p") || tempS.endsWith(",J") || tempS.endsWith(" J") || tempS.endsWith("K|p") || tempS.endsWith(",K") || tempS.endsWith(" K")) {
                    isPI = true;
                } else if (nextChar == ',' || nextChar == ' ') {
                    String nextPart = "";
                    try {
                        nextPart = text.substring(n + 1, n + 11).trim();
                    }
                    catch (IndexOutOfBoundsException e) {
                        nextPart = text.substring(n + 1).trim();
                    }
                    if (nextPart.contains("-")) {
                        int end = text.indexOf(".", n);
                        if (end < 0) {
                            end = text.length();
                        } else if (end < text.length() - 1 && Character.isDigit(text.charAt(end + 1)) && (end = text.indexOf(" ", end)) < 0) {
                            end = text.length();
                        }
                        String s = text.substring(n + 1, end);
                        int p = s.indexOf("-");
                        while (p >= 0) {
                            if (p < s.length() - 1 && Character.isLetter(s.charAt(p + 1))) {
                                s = s.substring(0, p).replace(",", "").replace("-", "").replace(" ", "");
                                if (!Str.isNumeric(s = s.toLowerCase().replace("and", "").trim())) break;
                                isPI = false;
                                break;
                            }
                            p = s.indexOf("-", p + 1);
                        }
                    }
                }
            }
        }
        return isPI;
    }

    public static boolean isPIplus(String text, int n) {
        int len = text.length();
        if (len <= 0 || n > len - 1 || n <= 0) {
            return false;
        }
        char c = text.charAt(n);
        if (c != '+') {
            return false;
        }
        char[] delims = new char[]{' ', ',', '.', ';', ':', ')', ']', '+', '&'};
        char prevChar = '\u0000';
        char nextChar = '\u0000';
        boolean isPIplus = false;
        prevChar = text.charAt(n - 1);
        if (Character.isDigit(prevChar) || prevChar == ')' || prevChar == '(') {
            if (n == text.length() - 1) {
                isPIplus = true;
            } else {
                nextChar = text.charAt(n + 1);
                String temp = text.substring(n + 1).trim().toLowerCase();
                if (nextChar == '(' && n < len - 2 && text.charAt(n + 2) == '+') {
                    isPIplus = true;
                } else if (temp.indexOf("and") == 0 || temp.indexOf("or") == 0) {
                    isPIplus = true;
                } else if (!Character.isLetterOrDigit(nextChar)) {
                    int i = 0;
                    while (i < delims.length) {
                        if (nextChar == delims[i]) {
                            isPIplus = true;
                            break;
                        }
                        ++i;
                    }
                }
            }
        }
        return isPIplus;
    }

    public static String translateQName(String name) {
        if (name.equals("Q-") || name.equals("QBM") || name.equals("QB-") || name.equals("QB")) {
            return "Q(\\ensuremath{\\beta^-})";
        }
        if (name.equals("Q+") || name.equals("QBP") || name.equals("QB+")) {
            return "Q(\\ensuremath{\\beta^+})";
        }
        if (name.equals("QEC")) {
            return "Q(\\ensuremath{\\varepsilon})";
        }
        if (name.equals("SN")) {
            return "S(n)";
        }
        if (name.equals("SP")) {
            return "S(p)";
        }
        if (name.equals("QA")) {
            return "Q(\\ensuremath{\\alpha})";
        }
        if (name.equals("S2N")) {
            return "S(2n)";
        }
        if (name.equals("S2P")) {
            return "S(2p)";
        }
        if (name.equals("QEP") || name.equals("QECP")) {
            return "Q(\\ensuremath{\\varepsilon}p)";
        }
        if (name.equals("QBN") || name.equals("QB-N") || name.equals("QBMN")) {
            return "Q(\\ensuremath{\\beta^-}n)";
        }
        if (name.equals("QBM2N") || name.equals("QB-2N") || name.equals("QB2N")) {
            return "Q(\\ensuremath{\\beta^-}2n)";
        }
        if (name.equals("QBP2P") || name.equals("QB+2P") || name.equals("QB2P")) {
            return "Q(\\ensuremath{\\beta^+}2p)";
        }
        if (name.equals("Q2B")) {
            return "Q(2\\ensuremath{\\beta^-})";
        }
        if (name.equals("Q4B")) {
            return "Q(4\\ensuremath{\\beta^-})";
        }
        if (name.equals("QDA")) {
            return "Q(d,\\ensuremath{\\alpha})";
        }
        if (name.equals("QPA")) {
            return "Q(p,\\ensuremath{\\alpha})";
        }
        if (name.equals("QNA")) {
            return "Q(n,\\ensuremath{\\alpha})";
        }
        return name;
    }
}
