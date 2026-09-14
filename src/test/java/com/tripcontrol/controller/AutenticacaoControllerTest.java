package com.tripcontrol.controller;

import com.tripcontrol.model.PerfilAcesso;
import com.tripcontrol.model.Usuario;
import com.tripcontrol.repository.UsuarioRepository;
import com.tripcontrol.repository.memory.InMemoryUsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cobre a autenticacao e o armazenamento seguro de senha exigidos pelo RNF04. */
class AutenticacaoControllerTest {

    private UsuarioRepository usuarioRepository;
    private AutenticacaoController controller;

    @BeforeEach
    void prepararCenario() {
        usuarioRepository = new InMemoryUsuarioRepository();
        controller = new AutenticacaoController(usuarioRepository);
        controller.cadastrarUsuario("Ana Silva", "ana.silva@tripcontrol.com",
                "tripcontrol", PerfilAcesso.FUNCIONARIO);
    }

    @Test
    @DisplayName("A senha nunca e guardada em texto puro")
    void armazenaSomenteHash() {
        Usuario usuario = usuarioRepository.buscarPorEmail("ana.silva@tripcontrol.com").orElseThrow();

        assertNotEquals("tripcontrol", usuario.getSenhaHash());
        assertTrue(usuario.getSenhaHash().startsWith("$2a$"));
    }

    @Test
    @DisplayName("Credenciais corretas iniciam a sessao")
    void autenticaComCredenciaisCorretas() {
        Resultado<Usuario> resultado = controller.autenticar("ana.silva@tripcontrol.com", "tripcontrol");

        assertTrue(resultado.isSucesso());
        assertTrue(controller.isAutenticado());
        assertEquals("Ana Silva", controller.nomeDoUsuarioAutenticado());
    }

    @Test
    @DisplayName("Senha incorreta e bloqueada sem revelar se o e-mail existe")
    void recusaSenhaIncorreta() {
        Resultado<Usuario> resultado = controller.autenticar("ana.silva@tripcontrol.com", "errada");

        assertEquals(StatusResultado.OPERACAO_BLOQUEADA, resultado.getStatus());
        assertEquals("E-mail ou senha invalidos.", resultado.getMensagem());
        assertFalse(controller.isAutenticado());
    }

    @Test
    @DisplayName("E-mail repetido nao gera segundo usuario")
    void recusaEmailDuplicado() {
        Resultado<Usuario> resultado = controller.cadastrarUsuario("Outra Pessoa",
                "ana.silva@tripcontrol.com", "outrasenha", PerfilAcesso.FUNCIONARIO);

        assertEquals(StatusResultado.DUPLICIDADE, resultado.getStatus());
        assertEquals(1L, usuarioRepository.contar());
    }
}
