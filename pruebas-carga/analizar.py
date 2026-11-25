"""Resume un .jtl (CSV) de JMeter para el QC de rendimiento (CP-REN / RD-01).

Uso:  python analizar.py resultados.jtl [umbral_ms=3000]

No cuenta los logins del setUp (etiqueta que empieza por LOGIN).
"""
import csv
import sys
from collections import Counter, defaultdict


def percentil(valores, p):
    if not valores:
        return 0
    orden = sorted(valores)
    k = max(0, min(len(orden) - 1, int(round(p / 100 * len(orden) + 0.5)) - 1))
    return orden[k]


def main():
    ruta = sys.argv[1]
    umbral = int(sys.argv[2]) if len(sys.argv) > 2 else 3000

    filas = []
    with open(ruta, newline="", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            if r["label"].startswith("LOGIN"):
                continue
            filas.append(r)
    if not filas:
        print("No hay solicitudes de carga en el archivo.")
        return

    tiempos = [int(r["elapsed"]) for r in filas]
    exito = [r["success"] == "true" for r in filas]
    ok_umbral = sum(1 for r, e in zip(filas, exito) if e and int(r["elapsed"]) < umbral)
    inicio = min(int(r["timeStamp"]) for r in filas)
    fin = max(int(r["timeStamp"]) + int(r["elapsed"]) for r in filas)
    total = len(filas)

    print(f"Total de solicitudes:        {total}")
    print(f"Duración medida:             {(fin - inicio) / 1000:.0f} s")
    print(f"Rendimiento:                 {total / ((fin - inicio) / 1000):.1f} solicitudes/s")
    print(f"Éxito (HTTP 200):            {100 * sum(exito) / total:.2f} %")
    print(f"Éxito y < {umbral} ms:          {100 * ok_umbral / total:.2f} %   <- criterio RD-01 (>= 95 %)")
    print(f"Promedio:                    {sum(tiempos) / total:.0f} ms")
    print(f"Mediana / P90:               {percentil(tiempos, 50)} / {percentil(tiempos, 90)} ms")
    print(f"P95 / P99 / Máx:             {percentil(tiempos, 95)} / {percentil(tiempos, 99)} / {max(tiempos)} ms")

    print("\nCódigos HTTP:")
    for codigo, n in Counter(r["responseCode"] for r in filas).most_common():
        print(f"  {codigo:>30}: {n} ({100 * n / total:.2f} %)")

    print("\nPor endpoint:")
    grupos = defaultdict(list)
    for r in filas:
        grupos[r["label"]].append(r)
    print(f"  {'endpoint':42} {'n':>6} {'error%':>7} {'prom':>6} {'p95':>6} {'p99':>6} {'<umbral%':>9}  códigos de error")
    for etiqueta, rs in sorted(grupos.items()):
        t = [int(r["elapsed"]) for r in rs]
        errores = [r for r in rs if r["success"] != "true"]
        dentro = sum(1 for r in rs if r["success"] == "true" and int(r["elapsed"]) < umbral)
        codigos = dict(Counter(r["responseCode"] for r in errores))
        print(f"  {etiqueta:42} {len(rs):>6} {100 * len(errores) / len(rs):>6.2f}% {sum(t) / len(t):>6.0f} "
              f"{percentil(t, 95):>6} {percentil(t, 99):>6} {100 * dentro / len(rs):>8.2f}%  {codigos or ''}")

    print(f"\nRD-01: {'CUMPLE' if ok_umbral / total >= 0.95 else 'NO CUMPLE'}")


if __name__ == "__main__":
    main()
