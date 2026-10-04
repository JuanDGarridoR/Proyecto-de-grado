package com.proyectogrado.messaging_backend.model;

/**
 * Tipos de notificación que envía VITA+. Cada servicio indica el tipo al
 * pedir el envío, y el usuario puede desactivar los que no quiera recibir
 * desde su perfil.
 */
public enum TipoNotificacion {

    /** Botón de emergencia de una persona mayor (a acompañantes y organizaciones). */
    EMERGENCIA,

    /** Felicitación de VITA+ en el cumpleaños del propio usuario. */
    CUMPLEANOS,

    /** Aviso de que una persona mayor vinculada cumple años (a acompañantes y organizaciones). */
    CUMPLEANOS_PERSONA_MAYOR,

    /** Recordatorio de una actividad en la que está inscrita la persona mayor. */
    ACTIVIDAD,

    /** Recordatorios de citas médicas (a la persona mayor y sus acompañantes). */
    CITA_MEDICA,

    /** Recordatorios de tomas de medicamentos (a la persona mayor y sus acompañantes). */
    MEDICAMENTO;

    /** El tipo con ese nombre, o null si no existe. */
    public static TipoNotificacion desde(String nombre) {
        if (nombre == null) {
            return null;
        }
        try {
            return valueOf(nombre.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
