package com.tripcontrol.controller;

import com.tripcontrol.model.PerfilAcesso;
import com.tripcontrol.model.Usuario;
import com.tripcontrol.repository.UsuarioRepository;
import com.tripcontrol.util.Formatadores;
import com.tripcontrol.util.SenhaUtils;

import java.util.Optional;

/**
 * Controle de acesso ao sistema (RNF04 e pre-condicao de todos os casos de uso).
 *
 * <p>A senha informada nunca e comparada em texto puro: o hash BCrypt
 * armazenado e conferido por {@link SenhaUtils}. A mensagem de erro e
 * deliberadamente generica para nao revelar se o e-mail existe.</p>
 */
public class AutenticacaoController {

    private final UsuarioRepository usuarioRepository;
    private Usuario usuarioAutenticado;

    public AutenticacaoController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /** Autentica o funcionario e inicia a sessao da aplicacao. */
    public Resultado<Usuario> autenticar(String email, String senha) {
        if (Formatadores.textoOuNulo(email) == null) {
            return Resultado.erroValidacao("email", "Informe o e-mail de acesso.");
        }
        if (Formatadores.textoOuNulo(senha) == null) {
            return Resultado.erroValidacao("senha", "Informe a senha.");
        }

        Optional<Usuario> encontrado = usuarioRepository.buscarPorEmail(email.trim());
        if (encontrado.isEmpty() || !SenhaUtils.conferir(senha, encontrado.get().getSenhaHash())) {
            return Resultado.operacaoBloqueada("E-mail ou senha invalidos.");
        }
        if (!encontrado.get().isAtivo()) {
            return Resultado.operacaoBloqueada("Usuario inativo. Procure o administrador da agencia.");
        }

        this.usuarioAutenticado = encontrado.get();
        return Resultado.sucesso(usuarioAutenticado, "Bem-vindo(a), " + usuarioAutenticado.getNome() + ".");
    }

    /** Cria um usuario ja com a senha convertida em hash. */
    public Resultado<Usuario> cadastrarUsuario(String nome, String email, String senha, PerfilAcesso perfil) {
        if (Formatadores.textoOuNulo(nome) == null) {
            return Resultado.erroValidacao("nome", "Informe o nome do usuario.");
        }
        if (Formatadores.textoOuNulo(email) == null) {
            return Resultado.erroValidacao("email", "Informe o e-mail do usuario.");
        }
        if (senha == null || senha.length() < 6) {
            return Resultado.erroValidacao("senha", "A senha deve ter ao menos 6 caracteres.");
        }
        if (usuarioRepository.buscarPorEmail(email.trim()).isPresent()) {
            return Resultado.duplicidade(usuarioRepository.buscarPorEmail(email.trim()).orElseThrow(),
                    "Ja existe um usuario com esse e-mail.");
        }

        Usuario usuario = new Usuario(nome.trim(), email.trim(), SenhaUtils.gerarHash(senha),
                perfil == null ? PerfilAcesso.FUNCIONARIO : perfil);
        usuarioRepository.salvar(usuario);
        return Resultado.sucesso(usuario, "Usuario cadastrado com sucesso.");
    }

    public void encerrarSessao() {
        this.usuarioAutenticado = null;
    }

    public boolean isAutenticado() {
        return usuarioAutenticado != null;
    }

    public Optional<Usuario> getUsuarioAutenticado() {
        return Optional.ofNullable(usuarioAutenticado);
    }

    /** @return nome do funcionario logado, usado nos registros de auditoria. */
    public String nomeDoUsuarioAutenticado() {
        return usuarioAutenticado == null ? "Sistema" : usuarioAutenticado.getNome();
    }
}
