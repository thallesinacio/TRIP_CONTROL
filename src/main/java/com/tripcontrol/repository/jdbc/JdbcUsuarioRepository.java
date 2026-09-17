package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.PerfilAcesso;
import com.tripcontrol.model.Usuario;
import com.tripcontrol.repository.RepositorioException;
import com.tripcontrol.repository.UsuarioRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de usuarios na tabela {@code Funcionario} (RNF04).
 *
 * <p>A tabela usa o CPF como chave primaria, entao gravar um usuario exige que
 * {@code Usuario.cpf} esteja preenchido. Hoje o unico usuario nasce da carga
 * inicial ({@code R__carga_inicial.sql}); um cadastro de funcionarios pela tela
 * ainda nao tem caso de uso (item F dos pontos de atencao do roadmap).</p>
 */
public class JdbcUsuarioRepository extends JdbcRepositorioBase implements UsuarioRepository {

    private static final String COLUNAS =
            "id, CPFFuncionario, nome, email, senha, perfil, ativo";

    @Override
    protected String nomeDaEntidade() {
        return "usuario";
    }

    @Override
    public Usuario salvar(Usuario usuario) {
        exigirCpf(usuario);
        executar("salvar", """
                insert into Funcionario (CPFFuncionario, nome, email, senha, perfil, ativo)
                values (?, ?, ?, ?, ?, ?)
                """,
                usuario.getCpf(), usuario.getNome(), usuario.getEmail(), usuario.getSenhaHash(),
                nomeDoPerfil(usuario), usuario.isAtivo());

        buscarPorEmail(usuario.getEmail()).ifPresent(gravado -> usuario.setId(gravado.getId()));
        return usuario;
    }

    @Override
    public Usuario atualizar(Usuario usuario) {
        exigirCpf(usuario);
        int linhas = executar("atualizar", """
                update Funcionario
                   set nome = ?, email = ?, senha = ?, perfil = ?, ativo = ?
                 where CPFFuncionario = ?
                """,
                usuario.getNome(), usuario.getEmail(), usuario.getSenhaHash(),
                nomeDoPerfil(usuario), usuario.isAtivo(), usuario.getCpf());

        if (linhas == 0) {
            throw new RepositorioException("Usuario inexistente para atualizacao: " + usuario.getEmail(), null);
        }
        return usuario;
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return buscarUm("select " + COLUNAS + " from Funcionario where id = ?", this::mapear, id);
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return buscarUm("select " + COLUNAS + " from Funcionario where lower(email) = lower(?)",
                this::mapear, email.trim());
    }

    @Override
    public List<Usuario> buscarTodos() {
        return buscarLista("select " + COLUNAS + " from Funcionario order by nome", this::mapear);
    }

    @Override
    public boolean remover(Long id) {
        return id != null && executar("remover", "delete from Funcionario where id = ?", id) > 0;
    }

    @Override
    public long contar() {
        return consultarEscalar("select count(*) from Funcionario", resultado -> resultado.getLong(1));
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private Usuario mapear(ResultSet resultado) throws SQLException {
        Usuario usuario = new Usuario();
        usuario.setId(lerLongOuNulo(resultado, "id"));
        usuario.setCpf(resultado.getString("cpffuncionario"));
        usuario.setNome(resultado.getString("nome"));
        usuario.setEmail(resultado.getString("email"));
        usuario.setSenhaHash(resultado.getString("senha"));
        String perfil = resultado.getString("perfil");
        usuario.setPerfil(perfil == null ? PerfilAcesso.FUNCIONARIO : PerfilAcesso.valueOf(perfil));
        usuario.setAtivo(resultado.getBoolean("ativo"));
        return usuario;
    }

    private static String nomeDoPerfil(Usuario usuario) {
        return usuario.getPerfil() == null ? PerfilAcesso.FUNCIONARIO.name() : usuario.getPerfil().name();
    }

    private static void exigirCpf(Usuario usuario) {
        if (usuario.getCpf() == null || usuario.getCpf().isBlank()) {
            throw new RepositorioException(
                    "O CPF do funcionario e obrigatorio para gravar no banco (e a chave primaria da tabela).",
                    null);
        }
    }
}
