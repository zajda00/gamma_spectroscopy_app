from __future__ import annotations

SYMBOLS = [
    None,
    "H", "He",
    "Li", "Be", "B", "C", "N", "O", "F", "Ne",
    "Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar",
    "K", "Ca", "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn",
    "Ga", "Ge", "As", "Se", "Br", "Kr",
    "Rb", "Sr", "Y", "Zr", "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd",
    "In", "Sn", "Sb", "Te", "I", "Xe",
    "Cs", "Ba", "La", "Ce", "Pr", "Nd", "Pm", "Sm", "Eu", "Gd", "Tb", "Dy",
    "Ho", "Er", "Tm", "Yb", "Lu",
    "Hf", "Ta", "W", "Re", "Os", "Ir", "Pt", "Au", "Hg",
    "Tl", "Pb", "Bi", "Po", "At", "Rn",
    "Fr", "Ra", "Ac", "Th", "Pa", "U", "Np", "Pu", "Am", "Cm", "Bk", "Cf",
    "Es", "Fm", "Md", "No", "Lr",
    "Rf", "Db", "Sg", "Bh", "Hs", "Mt", "Ds", "Rg", "Cn",
    "Nh", "Fl", "Mc", "Lv", "Ts", "Og",
]

Z_TO_SYMBOL = {z: sym for z, sym in enumerate(SYMBOLS) if sym is not None}
SYMBOL_TO_Z = {sym: z for z, sym in Z_TO_SYMBOL.items()}


def normalize_symbol(symbol: str) -> str:
    symbol = (symbol or "").strip()
    if not symbol:
        return ""
    if len(symbol) == 1:
        return symbol.upper()
    return symbol[0].upper() + symbol[1:].lower()


def symbol_from_z(z: int) -> str:
    return Z_TO_SYMBOL.get(int(z), "")


def z_from_symbol(symbol: str) -> int | None:
    symbol = normalize_symbol(symbol)
    return SYMBOL_TO_Z.get(symbol)


def neutrons_from_a_z(a: int | float, z: int | float) -> int:
    return int(a) - int(z)


def complete_from_symbol(a: int | float, symbol: str) -> dict:
    z = z_from_symbol(symbol)
    if z is None:
        raise ValueError(f"Unknown element symbol: {symbol}")
    n = neutrons_from_a_z(a, z)
    return {
        "A": int(a),
        "Z": int(z),
        "N": int(n),
        "symbol": normalize_symbol(symbol),
    }


def complete_from_z(a: int | float, z: int | float) -> dict:
    symbol = symbol_from_z(int(z))
    if not symbol:
        raise ValueError(f"Unknown atomic number Z: {z}")
    n = neutrons_from_a_z(a, z)
    return {
        "A": int(a),
        "Z": int(z),
        "N": int(n),
        "symbol": symbol,
    }
