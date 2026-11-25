"""Tiempos de respuesta SIN carga (un solo usuario) para RD-04 y RD-06.

30 peticiones secuenciales por endpoint con 1 s de pausa. La primera de cada
serie se reporta aparte ("en frío"). Solo GET, más el POST del PDF (que no
guarda nada). Un login por cuenta ficticia, separados 7 s.

Uso:  python tiempos_backend.py [salida.json]
"""
import base64
import json
import math
import random
import ssl
import statistics
import struct
import sys
import time
import urllib.error
import urllib.request
import zlib

BASE = "https://vita-plus.app"
N = 30
PAUSA = 1.0
CONTEXTO = ssl.create_default_context()
UA = "QC-tiempos-respuesta"


def pedir(metodo, ruta, token=None, cuerpo=None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    req = urllib.request.Request(BASE + ruta, data=datos, method=metodo)
    req.add_header("User-Agent", UA)
    req.add_header("Accept", "application/json, application/pdf")
    if datos is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    inicio = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=60, context=CONTEXTO) as r:
            contenido = r.read()
            codigo = r.status
    except urllib.error.HTTPError as e:
        contenido = e.read()
        codigo = e.code
    return codigo, (time.perf_counter() - inicio) * 1000, contenido


def login(correo):
    codigo, ms, contenido = pedir("POST", "/api/auth/login",
                                  cuerpo={"correo": correo, "contrasena": "Vita2025*"})
    if codigo != 200:
        sys.exit(f"Login falló para {correo}: HTTP {codigo} {contenido[:200]!r}")
    print(f"Login OK {correo} ({ms:.0f} ms)")
    return json.loads(contenido)["token"]


def png_grafica(ancho=1200, alto=600):
    """PNG ficticio del tamaño de una gráfica de ECharts (barras con ruido)."""
    random.seed(7)
    barras = [random.randint(80, alto - 60) for _ in range(12)]
    filas = []
    for y in range(alto):
        fila = bytearray([0])
        for x in range(ancho):
            b = x * 12 // ancho
            dentro = (alto - y) < barras[b] and (x % (ancho // 12)) > 14
            ruido = random.randint(0, 6)
            fila += bytes((40 + ruido, 110 + ruido, 180)) if dentro else bytes((250 - ruido, 250 - ruido, 250))
        filas.append(bytes(fila))

    def bloque(tipo, datos):
        return struct.pack(">I", len(datos)) + tipo + datos + struct.pack(">I", zlib.crc32(tipo + datos) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + bloque(b"IHDR", struct.pack(">IIBBBBB", ancho, alto, 8, 2, 0, 0, 0))
           + bloque(b"IDAT", zlib.compress(b"".join(filas), 6))
           + bloque(b"IEND", b""))
    return "data:image/png;base64," + base64.b64encode(png).decode()


def cuerpo_pdf():
    imagen = png_grafica()
    filas = [[f"Persona ficticia {i}", str(60 + i % 30), "Hipertensión" if i % 3 else "Ninguna"] for i in range(40)]
    return {
        "titulo": "Población (prueba QC de tiempos)",
        "descripcion": "Reporte de prueba con datos ficticios.",
        "periodo": "Últimos 90 días",
        "indicadores": [
            {"etiqueta": "Personas", "valor": "460", "detalle": "vinculadas", "tono": "neutro"},
            {"etiqueta": "Edad promedio", "valor": "72", "detalle": "años", "tono": "neutro"},
            {"etiqueta": "Viven solas", "valor": "18 %", "detalle": None, "tono": "alerta"},
            {"etiqueta": "Con EPS", "valor": "97 %", "detalle": None, "tono": "bueno"},
        ],
        "secciones": [
            {"titulo": f"Gráfica {k}", "descripcion": "Distribución ficticia", "imagen": imagen,
             "explicacion": "Texto de prueba.", "tabla": {"columnas": ["Nombre", "Edad", "Condición"], "filas": filas}}
            for k in range(1, 4)
        ],
        "nota": "Generado por la prueba de tiempos de respuesta del QC.",
    }


def percentil(valores, p):
    orden = sorted(valores)
    k = max(0, min(len(orden) - 1, math.ceil(p / 100 * len(orden)) - 1))
    return orden[k]


def medir(nombre, metodo, ruta, token, cuerpo=None):
    tiempos, codigos = [], []
    for i in range(N):
        codigo, ms, _ = pedir(metodo, ruta, token, cuerpo)
        tiempos.append(ms)
        codigos.append(codigo)
        if i < N - 1:
            time.sleep(PAUSA)
    resto = tiempos[1:]
    r = {
        "endpoint": nombre,
        "frio_ms": round(tiempos[0]),
        "min_ms": round(min(resto)),
        "mediana_ms": round(statistics.median(resto)),
        "promedio_ms": round(statistics.mean(resto)),
        "p95_ms": round(percentil(resto, 95)),
        "max_ms": round(max(resto)),
        "codigos": {str(c): codigos.count(c) for c in sorted(set(codigos))},
        "todos_ms": [round(t) for t in tiempos],
    }
    print(f"{nombre:42} frío {r['frio_ms']:>6}  min {r['min_ms']:>5}  med {r['mediana_ms']:>5}  "
          f"prom {r['promedio_ms']:>5}  p95 {r['p95_ms']:>5}  máx {r['max_ms']:>5}  {r['codigos']}", flush=True)
    return r


def main():
    salida = sys.argv[1] if len(sys.argv) > 1 else "resultados/tiempos_backend.json"
    pdf = cuerpo_pdf()
    print(f"Cuerpo del PDF: {len(json.dumps(pdf)) / 1024:.0f} KB")

    tok_pm = login("pm001@vitaplus.test")
    time.sleep(7)
    tok_org = login("org01@vitaplus.test")

    resultados = []
    for nombre, metodo, ruta, tok, cuerpo in [
        ("GET /api/auth/informacion", "GET", "/api/auth/informacion", tok_pm, None),
        ("GET /api/persona-mayor/signos-vitales", "GET", "/api/persona-mayor/signos-vitales", tok_pm, None),
        ("GET /api/actividades/disponibles", "GET", "/api/actividades/disponibles", tok_pm, None),
        ("GET /api/analitica/poblacion", "GET", "/api/analitica/poblacion", tok_org, None),
        ("GET /api/analitica/actividades", "GET", "/api/analitica/actividades", tok_org, None),
        ("GET /api/analitica/perfiles", "GET", "/api/analitica/perfiles", tok_org, None),
        ("POST /api/analitica/reportes/pdf", "POST", "/api/analitica/reportes/pdf", tok_org, pdf),
    ]:
        resultados.append(medir(nombre, metodo, ruta, tok, cuerpo))

    with open(salida, "w", encoding="utf-8") as f:
        json.dump(resultados, f, ensure_ascii=False, indent=2)
    print("Guardado en", salida)


if __name__ == "__main__":
    main()
