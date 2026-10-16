package com.proyectogrado.analitica_service.perfiles;

/**
 * Variables con las que K-means agrupa a las personas mayores, en el orden
 * de las columnas de la matriz de datos. Cada una pertenece a una dimensión
 * (edad, salud, signos vitales o acompañamiento) que sirve para nombrar los
 * grupos; peso dice cómo cuenta en esa dimensión (1 = más es más necesidad,
 * -1 = menos es más necesidad, 0 = solo entra al modelo).
 *
 * puedeFaltar: las que pueden no tener dato (la persona no lo registró).
 * Los conteos nunca faltan: 0 es "no registró ninguno".
 */
public enum VariablePerfil {

    EDAD("edad", "Edad", "años", Dimension.EDAD, 1, true),
    ENFERMEDADES("enfermedades", "Enfermedades crónicas", "", Dimension.SALUD, 1, false),
    DISCAPACIDADES("discapacidades", "Discapacidades", "", Dimension.SALUD, 1, false),
    CONDICIONES_SEVERAS("condicionesSeveras", "Condiciones severas", "", Dimension.SALUD, 1, false),
    MEDICAMENTOS("medicamentos", "Medicamentos activos", "", Dimension.SALUD, 1, false),
    PRESION_SISTOLICA("presionSistolica", "Presión sistólica promedio", "mmHg", Dimension.SIGNOS, 0, true),
    SATURACION_OXIGENO("saturacionOxigeno", "Saturación de oxígeno promedio", "%", Dimension.SIGNOS, -1, true),
    FRECUENCIA_CARDIACA("frecuenciaCardiaca", "Pulso promedio", "lpm", Dimension.SIGNOS, 0, true),
    IMC("imc", "Índice de masa corporal", "kg/m²", Dimension.SIGNOS, 0, true),
    FUERA_DE_RANGO("fueraDeRango", "Mediciones fuera de rango", "%", Dimension.SIGNOS, 1, true),
    VIVE_SOLO("viveSolo", "Vive sola", "%", Dimension.ACOMPANAMIENTO, 1, true),
    ACOMPANANTES("acompanantes", "Acompañantes vinculados", "", Dimension.ACOMPANAMIENTO, -1, false),
    CITAS_CON_ACOMPANAMIENTO("citasConAcompanamiento", "Citas que piden acompañamiento", "%",
            Dimension.ACOMPANAMIENTO, 1, true),
    EMERGENCIAS("emergencias", "Emergencias en 90 días", "", Dimension.ACOMPANAMIENTO, 1, false);

    /** Grupo de variables; con ellas se nombran y se describen los perfiles. */
    public enum Dimension {
        EDAD("Edad"),
        SALUD("Condiciones de salud"),
        SIGNOS("Signos vitales"),
        ACOMPANAMIENTO("Necesidades de acompañamiento");

        private final String nombre;

        Dimension(String nombre) {
            this.nombre = nombre;
        }

        public String nombre() {
            return nombre;
        }
    }

    private final String clave;
    private final String nombre;
    private final String unidad;
    private final Dimension dimension;
    private final int peso;
    private final boolean puedeFaltar;

    VariablePerfil(String clave, String nombre, String unidad, Dimension dimension, int peso, boolean puedeFaltar) {
        this.clave = clave;
        this.nombre = nombre;
        this.unidad = unidad;
        this.dimension = dimension;
        this.peso = peso;
        this.puedeFaltar = puedeFaltar;
    }

    public String clave() {
        return clave;
    }

    public String nombre() {
        return nombre;
    }

    public String unidad() {
        return unidad;
    }

    public Dimension dimension() {
        return dimension;
    }

    public int peso() {
        return peso;
    }

    public boolean puedeFaltar() {
        return puedeFaltar;
    }
}
