package com.actividades.actividadSpring;

import com.turismo.common.ConflictoNegocioException;
import com.turismo.common.RecursoNoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

public class ActividadNoEncontradaException extends RecursoNoEncontradoException {
    public ActividadNoEncontradaException(Long id) { super("No existe la actividad " + id); }
}


