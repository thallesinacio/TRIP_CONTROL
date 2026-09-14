package com.tripcontrol.repository;

import com.tripcontrol.model.Recurso;
import com.tripcontrol.model.TipoItemItinerario;

import java.util.List;

/** Cadastro de hospedagens, transportes e atividades reutilizaveis (UC06). */
public interface RecursoRepository extends Repositorio<Recurso> {

    List<Recurso> buscarPorTipo(TipoItemItinerario tipo);
}
