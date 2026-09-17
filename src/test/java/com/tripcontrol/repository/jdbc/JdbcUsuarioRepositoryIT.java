package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.PerfilAcesso;
import com.tripcontrol.model.Usuario;
import com.tripcontrol.repository.RepositorioException;
import com.tripcontrol.util.SenhaUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Persistencia de usuarios na tabela Funcionario (RNF04). */
class JdbcUsuarioRepositoryIT extends RepositorioJdbcIT {

    private final JdbcUsuarioRepository repositorio = new JdbcUsuarioRepository();

    private Usuario novoUsuario(String cpf, String email) {
        Usuario usuario = new Usuario("Theo Pereira", email, SenhaUtils.gerarHash("tripcontrol"),
                PerfilAcesso.FUNCIONARIO);
        usuario.setCpf(cpf);
        return usuario;
    }

    @Test
    @DisplayName("Usuario gravado volta com id, perfil, ativo e hash intactos")
    void gravaERecupera() {
        Usuario gravado = repositorio.salvar(novoUsuario("11122233344", "theo@tripcontrol.com"));

        Optional<Usuario> lido = repositorio.buscarPorEmail("THEO@tripcontrol.com");
        assertTrue(lido.isPresent(), "a busca por e-mail deve ignorar a caixa");
        assertEquals(gravado.getSenhaHash(), lido.orElseThrow().getSenhaHash());
        assertEquals(PerfilAcesso.FUNCIONARIO, lido.orElseThrow().getPerfil());
        assertTrue(lido.orElseThrow().isAtivo());
        assertTrue(SenhaUtils.conferir("tripcontrol", lido.orElseThrow().getSenhaHash()));
    }

    @Test
    @DisplayName("Usuario inativado permanece inativo no banco")
    void persisteUsuarioInativo() {
        Usuario usuario = repositorio.salvar(novoUsuario("55566677788", "inativo@tripcontrol.com"));
        usuario.setAtivo(false);
        repositorio.atualizar(usuario);

        assertFalse(repositorio.buscarPorEmail("inativo@tripcontrol.com").orElseThrow().isAtivo());
    }

    @Test
    @DisplayName("Sem CPF a gravacao e recusada com mensagem clara")
    void exigeCpf() {
        Usuario semCpf = new Usuario("Sem CPF", "semcpf@tripcontrol.com", "hash", PerfilAcesso.FUNCIONARIO);

        RepositorioException erro = assertThrows(RepositorioException.class, () -> repositorio.salvar(semCpf));
        assertTrue(erro.getMessage().contains("CPF"));
    }
}
