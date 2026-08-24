import ensdfparser.calc.XDX;
import ensdfparser.ensdf.ENSDF;
import ensdfparser.ensdf.Nucleus;
import ensdfparser.ensdf.SDS2XDX;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.util.Str;
import ensdfparser.nds.latex.Translator;
import radreport.decay.DecayDataset;
import radreport.decay.DecaySpectrum;
import radreport.decay.RADControl;
import radreport.main.Setup;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

public class RadiationReportBridge {
    public static void main(String[] args) {
        try {
            Setup.load();
            Translator.init();
            Map<String, String> values = parseArgs(args);
            String parent = values.getOrDefault("parent", "").trim();
            String daughter = values.getOrDefault("daughter", "").trim();
            String uniqueness = values.getOrDefault("uniqueness", "").trim();
            String halfLifeUnit = values.getOrDefault("half_life_unit", "S").trim();
            if (uniqueness != null && !uniqueness.isEmpty()) {
                uniqueness = uniqueness.toUpperCase();
                if ("NO".equals(uniqueness)) {
                    uniqueness = "";
                }
            }
            if (halfLifeUnit != null && !halfLifeUnit.isEmpty()) {
                halfLifeUnit = halfLifeUnit.toUpperCase();
            }
            if (parent.isEmpty() || daughter.isEmpty()) {
                emitError("missing_nuclide", "parent and daughter must be provided");
                return;
            }

            String parentValue = parent;
            String daughterValue = daughter;
            try {
                Nucleus parentNucleus = new Nucleus(parentValue);
                if (parentNucleus.A() <= 0 || parentNucleus.Z() <= 0) {
                    emitError("invalid_parent", "parent nuclide is invalid: " + parentValue);
                    return;
                }
            } catch (Exception e) {
                emitError("invalid_parent", "parent nuclide is invalid: " + parentValue + " (" + e.getMessage() + ")");
                return;
            }

            try {
                Nucleus daughterNucleus = new Nucleus(daughterValue);
                if (daughterNucleus.Z() <= 0 || daughterNucleus.A() <= 0) {
                    emitError("invalid_daughter", "daughter atomic number is invalid: " + daughterValue);
                    return;
                }
            } catch (Exception e) {
                emitError("invalid_daughter", "daughter nuclide is invalid: " + daughterValue + " (" + e.getMessage() + ")");
                return;
            }

            double q = parseRequiredDouble(values, "q", "Q value");
            double dq = parseOptionalDouble(values, "dq", "Q uncertainty");
            double parentEnergy = parseRequiredDouble(values, "parent_energy", "parent excitation energy");
            double dParentEnergy = parseOptionalDouble(values, "dparent_energy", "parent excitation-energy uncertainty");
            double halfLife = parseRequiredDouble(values, "half_life", "half-life");
            double dHalfLife = parseOptionalDouble(values, "dhalf_life", "half-life uncertainty");
            double levelEnergy = parseRequiredDouble(values, "level_energy", "daughter level energy");
            double dLevelEnergy = parseOptionalDouble(values, "dlevel_energy", "daughter level-energy uncertainty");
            double betaIntensity = parseRequiredDouble(values, "beta_intensity", "beta intensity");
            double dBetaIntensity = parseOptionalDouble(values, "dbeta_intensity", "beta-intensity uncertainty");

            if (q <= 0.0) {
                emitError("invalid_endpoint", "Q value must be positive");
                return;
            }
            double endpoint = q + parentEnergy - levelEnergy;
            if (endpoint <= 0.0) {
                emitError("invalid_endpoint", "endpoint must be positive; computed endpoint = " + endpoint);
                return;
            }
            if (halfLife <= 0.0) {
                emitError("invalid_half_life", "half-life must be positive");
                return;
            }
            if (betaIntensity <= 0.0) {
                emitError("invalid_beta_intensity", "beta intensity must be positive");
                return;
            }

            String parentJpi = values.getOrDefault("parent_jpi", "").trim();
            String daughterJpi = values.getOrDefault("daughter_jpi", "").trim();
            String decayMode = values.getOrDefault("decay_mode", "B-").trim();

            Vector<String> linesV = new Vector<String>();
            String prefix = Str.makeENSDFLinePrefix(daughter, "   ");
            String decayHeader = prefix + parent + " " + decayMode + " DECAY";
            linesV.add(decayHeader);

            SDS2XDX qValue = SDS2XDX.parse(Double.toString(q), Double.toString(dq), false);
            SDS2XDX parentEnergyValue = SDS2XDX.parse(Double.toString(parentEnergy), Double.toString(dParentEnergy), false);
            SDS2XDX halfLifeValue = SDS2XDX.parse(Double.toString(halfLife), Double.toString(dHalfLife), false);
            SDS2XDX levelEnergyValue = SDS2XDX.parse(Double.toString(levelEnergy), Double.toString(dLevelEnergy), false);
            SDS2XDX betaIntensityValue = SDS2XDX.parse(Double.toString(betaIntensity), Double.toString(dBetaIntensity), false);

            if (qValue == null || halfLifeValue == null || levelEnergyValue == null || betaIntensityValue == null) {
                emitError("parse_error", "Q, half-life, level energy, and beta intensity must be numeric");
                return;
            }

            String[] parentRecord = buildParentRecord(parent, parentJpi, parentEnergyValue, halfLifeValue, halfLifeUnit, qValue);
            String pLine = parentRecord[0];
            String pCRLine = parentRecord[1];
            if (pLine == null || pLine.isEmpty()) {
                emitError("parent_record_error", "unable to build parent ENSDF record");
                return;
            }

            String[] normRecord = buildNormalizationRecord(daughter);
            String normLine = normRecord[0];
            String normCRLine = normRecord[1];

            String[] levRecord = buildLevelRecord(daughter, daughterJpi, levelEnergyValue);
            String levLine = levRecord[0];
            String levCRLine = levRecord[1];

            String[] betaRecord = buildBetaRecord(daughter, betaIntensityValue, uniqueness, decayMode);
            String betaLine = betaRecord[0];
            String betaCRLine = betaRecord[1];

            linesV.add(pLine);
            if (!pCRLine.isEmpty()) {
                linesV.add(pCRLine);
            }
            linesV.add(normLine);
            if (!normCRLine.isEmpty()) {
                linesV.add(normCRLine);
            }
            if (levLine == null || levLine.isEmpty()) {
                emitError("level_record_error", "unable to build daughter level ENSDF record");
                return;
            }
            linesV.add(levLine);
            if (!levCRLine.isEmpty()) {
                linesV.add(levCRLine);
            }
            if (betaLine == null || betaLine.isEmpty()) {
                emitError("beta_record_error", "unable to build beta ENSDF record");
                return;
            }
            linesV.add(betaLine);
            if (!betaCRLine.isEmpty()) {
                linesV.add(betaCRLine);
            }

            ENSDF ens = new ENSDF();
            ens.setValues(linesV);
            DecayDataset dataset = new DecayDataset(ens);
            DecaySpectrum spectrum = dataset.spectrumAt(0);
            if (spectrum == null) {
                emitError("spectrum_missing", "spectrumAt(0) returned null for the generated ENSDF record");
                return;
            }

            RADControl.calculateBremsstrahlung = false;

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("ok", true);
            payload.put("parent", parent);
            payload.put("daughter", daughter);
            payload.put("decay_mode", decayMode);
            payload.put("endpoint_keV", endpoint);
            payload.put("fTotal", toXdxMap(spectrum.fTotal()));
            payload.put("ft", toXdxMap(spectrum.ft()));
            payload.put("logft", toXdxMap(spectrum.logft()));
            payload.put("partialT12", toXdxMap(spectrum.partialT12()));
            payload.put("calcIBeta", toXdxMap(spectrum.calcIBeta()));
            payload.put("calcIEC", toXdxMap(spectrum.calcIEC()));
            payload.put("isAllowed", spectrum.isAllowed());
            payload.put("isUnique", spectrum.isUnique());
            payload.put("isNonUnique", spectrum.isNonUnique());
            payload.put("nForbiddenUnique", spectrum.nForbiddenUnique());
            payload.put("nForbidden", spectrum.nForbidden());
            payload.put("uniqueness", uniqueness);
            payload.put("backend", "radiation_report");

            System.out.println(toJson(payload));
        } catch (Exception e) {
            e.printStackTrace();
            emitError("bridge_exception", e.toString());
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String arg : args) {
            if (arg == null || arg.trim().isEmpty()) {
                continue;
            }
            int idx = arg.indexOf('=');
            if (idx < 0) {
                throw new IllegalArgumentException("invalid argument, expected key=value: " + arg);
            }
            String key = arg.substring(0, idx).trim();
            String value = arg.substring(idx + 1).trim();
            values.put(key, value);
        }
        return values;
    }

    private static double parseRequiredDouble(Map<String, String> values, String key, String label) {
        String raw = values.get(key);
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(label + " is not numeric: " + raw);
        }
    }
    private static double parseOptionalDouble(Map<String, String> values, String key, String label) {
        String raw = values.get(key);
        if (raw == null || raw.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(label + " is not numeric: " + raw);
        }
    }


    private static String[] buildParentRecord(String parent, String parentJpi, SDS2XDX parentEnergyValue, SDS2XDX halfLifeValue, String halfLifeUnit, SDS2XDX qValue) {
        String prefix = Str.makeENSDFLinePrefix(parent, "  P");
        String line = prefix + Str.repeat(" ", 71);
        String continuation = "";

        if (parentEnergyValue != null) {
            String cleared = EnsdfUtil.replaceEnergyOfRecordLine(line, "", "");
            if (parentEnergyValue.s().length() <= 10 && parentEnergyValue.ds().length() <= 2) {
                line = EnsdfUtil.replaceEnergyOfRecordLine(cleared, parentEnergyValue.s(), parentEnergyValue.ds());
            } else {
                continuation = "E=" + parentEnergyValue.s() + " " + parentEnergyValue.ds();
            }
        }

        if (parentJpi != null && !parentJpi.trim().isEmpty()) {
            line = EnsdfUtil.replaceJPIOfLevelLine(line, parentJpi.trim());
        }

        if (halfLifeValue != null) {
            int n = halfLifeValue.s().length() + halfLifeUnit.length() + 1;
            if (n <= 10 && halfLifeValue.ds().length() <= 6) {
                line = EnsdfUtil.replaceHalflifeOfLevelLine(line, halfLifeValue.s(), halfLifeValue.ds(), halfLifeUnit.trim().toUpperCase());
            } else {
                continuation = continuation.isEmpty() ? "T=" + halfLifeValue.s() + " " + halfLifeUnit.toUpperCase() + " " + halfLifeValue.ds() : continuation + "$T=" + halfLifeValue.s() + " " + halfLifeUnit.toUpperCase() + " " + halfLifeValue.ds();
            }
        }

        if (qValue != null && line.length() > 0) {
            if (qValue.s().length() <= 10 && qValue.ds().length() <= 2) {
                line = EnsdfUtil.replaceC2SOfLevelLine(line, qValue.s(), qValue.ds());
            } else {
                continuation = continuation.isEmpty() ? "QP=" + qValue.s() + " " + qValue.ds() : continuation + "$QP=" + qValue.s() + " " + qValue.ds();
            }
        }

        if (!continuation.isEmpty()) {
            String continuationPrefix = Str.makeENSDFLinePrefix(parent, "2 P");
            continuation = continuationPrefix + continuation.trim();
        }

        return new String[] { line, continuation };
    }

    private static String[] buildNormalizationRecord(String daughter) {
        String prefix = Str.makeENSDFLinePrefix(daughter, "  N");
        String line = prefix + Str.repeat(" ", 71);
        String continuation = "";

        SDS2XDX brValue = SDS2XDX.parse("100", "", false);
        if (brValue != null) {
            String normalizedLine = EnsdfUtil.replaceEnergyOfRecordLine(line, "", "");
            SDS2XDX normalized = brValue.divided(100.0f);
            if (normalized != null && normalized.s().length() <= 8 && normalized.ds().length() <= 2) {
                normalizedLine = EnsdfUtil.replaceIEOfECBPLine(normalizedLine, normalized.s(), normalized.ds());
                line = normalizedLine;
            } else {
                continuation = "BR=" + normalized.s() + " " + normalized.ds();
            }
        }

        if (!continuation.isEmpty()) {
            String continuationPrefix = Str.makeENSDFLinePrefix(daughter, "2 N");
            continuation = continuationPrefix + continuation.trim();
        }

        return new String[] { line, continuation };
    }

    private static String[] buildLevelRecord(String daughter, String daughterJpi, SDS2XDX levelEnergyValue) {
        String prefix = Str.makeENSDFLinePrefix(daughter, "  L");
        String line = prefix + Str.repeat(" ", 71);
        String continuation = "";

        if (levelEnergyValue != null) {
            String cleared = EnsdfUtil.replaceEnergyOfRecordLine(line, "", "");
            if (levelEnergyValue.s().length() <= 10 && levelEnergyValue.ds().length() <= 2) {
                line = EnsdfUtil.replaceEnergyOfRecordLine(cleared, levelEnergyValue.s(), levelEnergyValue.ds());
            } else {
                continuation = "E=" + levelEnergyValue.s() + " " + levelEnergyValue.ds();
            }
        }

        if (daughterJpi != null && !daughterJpi.trim().isEmpty()) {
            line = EnsdfUtil.replaceJPIOfLevelLine(line, daughterJpi.trim());
        }

        if (!continuation.isEmpty()) {
            String continuationPrefix = Str.makeENSDFLinePrefix(daughter, "2 L");
            continuation = continuationPrefix + continuation.trim();
        }

        return new String[] { line, continuation };
    }

    private static String[] buildBetaRecord(String daughter, SDS2XDX betaIntensityValue, String uniqueness, String decayMode) {
        String prefix = Str.makeENSDFLinePrefix(daughter, "  " + decayMode.charAt(0));
        String line = prefix + Str.repeat(" ", 71);
        String continuation = "";

        String cleared = EnsdfUtil.replaceEnergyOfRecordLine(line, "", "");
        if (betaIntensityValue != null && betaIntensityValue.s().length() <= 8 && betaIntensityValue.ds().length() <= 2) {
            line = EnsdfUtil.replaceRIOfRecordLine(cleared, betaIntensityValue.s(), betaIntensityValue.ds());
        } else if (betaIntensityValue != null) {
            continuation = "IB=" + betaIntensityValue.s() + " " + betaIntensityValue.ds();
        }

        if (uniqueness != null && uniqueness.length() == 2) {
            line = Str.fixLineLength(line, 80);
            if (line.length() >= 79) {
                line = line.substring(0, 77) + uniqueness + line.substring(79);
            }
        }

        if (!continuation.isEmpty()) {
            String continuationPrefix = Str.makeENSDFLinePrefix(daughter, "2 " + decayMode.charAt(0));
            continuation = continuationPrefix + continuation.trim();
        }

        return new String[] { line, continuation };
    }

    private static String[] splitContinuation(String combined) {
        if (combined == null) {
            return new String[] { "", "" };
        }
        int marker = combined.indexOf('\u0000');
        if (marker < 0) {
            return new String[] { combined, "" };
        }
        return new String[] { combined.substring(0, marker), combined.substring(marker + 1) };
    }

    private static Map<String, Object> toXdxMap(XDX value) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (value == null) {
            out.put("x", null);
            out.put("dxl", null);
            out.put("dxu", null);
            out.put("s", null);
            return out;
        }
        out.put("x", value.x);
        out.put("dxl", value.dxl());
        out.put("dxu", value.dxu());
        out.put("s", value.s());
        return out;
    }

    private static void emitError(String error, String details) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("ok", false);
        payload.put("error", error);
        payload.put("details", details);
        System.out.println(toJson(payload));
    }

    private static String toJson(Map<String, Object> input) {
        StringBuilder out = new StringBuilder();
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('"').append(escapeJson(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value == null) {
                out.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                out.append(value.toString());
            } else if (value instanceof Map) {
                out.append(toJsonObject((Map<?, ?>) value));
            } else if (value instanceof Iterable) {
                out.append(toJsonArray((Iterable<?>) value));
            } else {
                out.append('"').append(escapeJson(value.toString())).append('"');
            }
        }
        out.append('}');
        return out.toString();
    }

    private static String toJsonObject(Map<?, ?> input) {
        StringBuilder out = new StringBuilder();
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('"').append(escapeJson(String.valueOf(entry.getKey()))).append('"').append(':');
            Object value = entry.getValue();
            if (value == null) {
                out.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                out.append(value.toString());
            } else if (value instanceof Map) {
                out.append(toJsonObject((Map<?, ?>) value));
            } else if (value instanceof Iterable) {
                out.append(toJsonArray((Iterable<?>) value));
            } else {
                out.append('"').append(escapeJson(value.toString())).append('"');
            }
        }
        out.append('}');
        return out.toString();
    }

    private static String toJsonArray(Iterable<?> values) {
        StringBuilder out = new StringBuilder();
        out.append('[');
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                out.append(',');
            }
            first = false;
            if (value == null) {
                out.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                out.append(value.toString());
            } else if (value instanceof Map) {
                out.append(toJsonObject((Map<?, ?>) value));
            } else if (value instanceof Iterable) {
                out.append(toJsonArray((Iterable<?>) value));
            } else {
                out.append('"').append(escapeJson(value.toString())).append('"');
            }
        }
        out.append(']');
        return out.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); ++i) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
            }
        }
        return out.toString();
    }
}
