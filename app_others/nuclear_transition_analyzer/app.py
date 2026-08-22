from __future__ import annotations

import io

import pandas as pd
import streamlit as st

from nuclear_transition_analyzer.analysis import compare_two_transitions, scan_table
from nuclear_transition_analyzer.data import load_csv

st.set_page_config(page_title="Nuclear Transition Analyzer", layout="wide")

st.title("Nuclear Transition Analyzer")
st.caption("Analiza typów przejść, multipolowości, reguł wyboru i stosunków Weisskopfa na podstawie intensywności samych gamma.")

with st.sidebar:
    st.header("Dane")
    uploaded = st.file_uploader("Wczytaj CSV", type=["csv"])
    sample = st.checkbox("Użyj przykładowego AI_processing_2_v2.csv", value=uploaded is None)
    A = st.number_input("Liczba masowa A", min_value=1, max_value=300, value=122)
    tolerance = st.number_input("Tolerancja dopasowania energii [keV]", min_value=0.01, max_value=5.0, value=0.5, step=0.05)
    intensity_col = st.text_input("Kolumna intensywności gamma", value="relative (%)")

@st.cache_data
def _load_from_bytes(data: bytes, intensity: str):
    return load_csv(io.BytesIO(data), intensity_col=intensity)

try:
    if uploaded is not None:
        df, cmap = _load_from_bytes(uploaded.getvalue(), intensity_col)
    elif sample:
        df, cmap = load_csv("examples/AI_processing_2_v2.csv", intensity_col=intensity_col)
    else:
        st.info("Wczytaj plik CSV, żeby rozpocząć analizę.")
        st.stop()
except Exception as exc:
    st.error(f"Nie udało się wczytać danych: {exc}")
    st.stop()

st.success(f"Wczytano {len(df)} wierszy. Energia: `{cmap.energy}`, intensywność gamma: `{cmap.intensity}`.")

main_tab, scan_tab, info_tab = st.tabs(["Porównanie dwóch gamm", "Przegląd tabeli", "Założenia fizyczne"])

with main_tab:
    st.subheader("Porównanie dwóch przejść")
    c1, c2, c3 = st.columns([1, 1, 1])
    with c1:
        e1 = st.number_input("Energia gamma 1 [keV]", value=650.05, step=0.01, format="%.2f")
    with c2:
        e2 = st.number_input("Energia gamma 2 [keV]", value=848.72, step=0.01, format="%.2f")
    with c3:
        nres = st.slider("Liczba wariantów", min_value=5, max_value=50, value=15)

    if st.button("Analizuj", type="primary"):
        try:
            transitions, result = compare_two_transitions(df, cmap, e1, e2, A=A, tolerance_keV=tolerance, max_results=nres)
            st.markdown("### Dopasowane przejścia")
            cols = st.columns(2)
            for col, t in zip(cols, transitions):
                with col:
                    st.metric(f"Eγ = {t.energy:.2f} keV", f"Iγ = {t.intensity_gamma:.4g}")
                    st.write(f"Poziomy: {t.from_energy} -> {t.to_energy} keV")
                    st.write(f"Spin początkowy: {t.from_spin_summary}")
                    st.write(f"Spin końcowy: {t.to_spin_summary}")
            st.markdown("### Najbardziej sensowne warianty")
            st.dataframe(result, use_container_width=True, hide_index=True)
            st.caption("Weisskopf jest używany jako test rzędu wielkości. Wariant zgodny w obrębie około jednego rzędu wielkości należy traktować jako możliwy, nie jako dowód.")
        except Exception as exc:
            st.error(str(exc))

with scan_tab:
    st.subheader("Kompaktowy przegląd reguł wyboru")
    max_rows = st.slider("Maksymalna liczba wierszy", 20, 500, 150)
    table = scan_table(df, cmap, A=A, max_rows=max_rows)
    st.dataframe(table, use_container_width=True, hide_index=True)

with info_tab:
    st.markdown(
        """
### Co aplikacja traktuje jako pewne
- Spin bez nawiasów, np. `2+`, jest twardym ograniczeniem.
- Spin w nawiasach, np. `(4+)`, `((3+))`, `(3,4+),(5-)`, jest tylko sugestią.
- Brak spinu oznacza brak ograniczenia spinowego.

### Reguły wyboru
- Warunek momentu pędu: `|Ji - Jf| <= L <= Ji + Jf`.
- Przejście gamma `0 -> 0` jest wykluczone.
- Dla przejść elektrycznych: parzystość zmienia się jak `(-1)^L`.
- Dla przejść magnetycznych: parzystość zmienia się jak `(-1)^(L+1)`.

### Interpretacja wyników
Aplikacja porównuje stosunek intensywności gamma z ilorazem przybliżonych szybkości Weisskopfa. To nie jest precyzyjne wyznaczenie multipolowości, tylko filtr fizycznie sensownych wariantów i narzędzie do wykrywania wariantów bardzo nieprawdopodobnych.
        """
    )
