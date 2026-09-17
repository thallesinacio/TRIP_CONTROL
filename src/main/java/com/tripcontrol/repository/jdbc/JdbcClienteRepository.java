package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.Cliente;
import com.tripcontrol.repository.ClienteRepository;
import com.tripcontrol.repository.RepositorioException;
import com.tripcontrol.util.ValidadorCpf;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de clientes (UC02).
 *
 * <p>Na tabela o CPF e a chave primaria; a coluna {@code id} existe para casar com
 * a identidade numerica usada pelo dominio e pelas interfaces de repositorio.
 * As preferencias ficam na tabela filha {@code ClientePreferencia}, porque sao
 * varias por cliente e o UC08 ainda vai filtrar por elas.</p>
 */
public class JdbcClienteRepository extends JdbcRepositorioBase implements ClienteRepository {

    private static final String COLUNAS =
            "id, CPFCliente, nome, telefone, email, endereco, dataCadastro";

    @Override
    protected String nomeDaEntidade() {
        return "cliente";
    }

    @Override
    public Cliente salvar(Cliente cliente) {
        long id = inserirRetornandoChave("""
                insert into Cliente (CPFCliente, nome, telefone, email, endereco, dataCadastro)
                values (?, ?, ?, ?, ?, ?)
                returning id
                """,
                ValidadorCpf.apenasDigitos(cliente.getCpf()), cliente.getNome(), cliente.getTelefone(),
                cliente.getEmail(), cliente.getEndereco(), dataDeCadastro(cliente));

        cliente.setId(id);
        regravarPreferencias(cliente);
        return cliente;
    }

    @Override
    public Cliente atualizar(Cliente cliente) {
        int linhas = executar("atualizar", """
                update Cliente
                   set CPFCliente = ?, nome = ?, telefone = ?, email = ?, endereco = ?
                 where id = ?
                """,
                ValidadorCpf.apenasDigitos(cliente.getCpf()), cliente.getNome(), cliente.getTelefone(),
                cliente.getEmail(), cliente.getEndereco(), cliente.getId());

        if (linhas == 0) {
            throw new RepositorioException("Cliente inexistente para atualizacao: id=" + cliente.getId(), null);
        }
        regravarPreferencias(cliente);
        return cliente;
    }

    @Override
    public Optional<Cliente> buscarPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return buscarUm("select " + COLUNAS + " from Cliente where id = ?", this::mapear, id);
    }

    @Override
    public List<Cliente> buscarTodos() {
        return buscarLista("select " + COLUNAS + " from Cliente order by nome", this::mapear);
    }

    @Override
    public boolean remover(Long id) {
        return id != null && executar("remover", "delete from Cliente where id = ?", id) > 0;
    }

    @Override
    public long contar() {
        return consultarEscalar("select count(*) from Cliente", resultado -> resultado.getLong(1));
    }

    @Override
    public Optional<Cliente> buscarPorCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return Optional.empty();
        }
        return buscarUm("select " + COLUNAS + " from Cliente where CPFCliente = ?",
                this::mapear, ValidadorCpf.apenasDigitos(cpf));
    }

    @Override
    public List<Cliente> buscarPorNome(String trechoDoNome) {
        if (trechoDoNome == null || trechoDoNome.isBlank()) {
            return buscarTodos();
        }
        return buscarLista("select " + COLUNAS + " from Cliente where nome ilike ? order by nome",
                this::mapear, "%" + trechoDoNome.trim() + "%");
    }

    @Override
    public List<Cliente> buscarPorPreferencia(String preferencia) {
        if (preferencia == null || preferencia.isBlank()) {
            return buscarTodos();
        }
        return buscarLista("""
                select c.id, c.CPFCliente, c.nome, c.telefone, c.email, c.endereco, c.dataCadastro
                  from Cliente c
                  join ClientePreferencia p on p.fkCPFCliente = c.CPFCliente
                 where lower(p.preferencia) = lower(?)
                 order by c.nome
                """, this::mapear, preferencia.trim());
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private Cliente mapear(ResultSet resultado) throws SQLException {
        Cliente cliente = new Cliente();
        cliente.setId(resultado.getLong("id"));
        cliente.setCpf(resultado.getString("cpfcliente"));
        cliente.setNome(resultado.getString("nome"));
        cliente.setTelefone(resultado.getString("telefone"));
        cliente.setEmail(resultado.getString("email"));
        cliente.setEndereco(resultado.getString("endereco"));
        cliente.setDataCadastro(resultado.getObject("datacadastro", LocalDateTime.class));
        cliente.definirPreferencias(buscarPreferencias(cliente.getCpf()));
        return cliente;
    }

    private List<String> buscarPreferencias(String cpf) {
        return buscarLista("""
                select preferencia from ClientePreferencia where fkCPFCliente = ? order by preferencia
                """, resultado -> resultado.getString("preferencia"), cpf);
    }

    /** Regrava a lista inteira: sao poucas por cliente e evita diferencial complicado. */
    private void regravarPreferencias(Cliente cliente) {
        String cpf = ValidadorCpf.apenasDigitos(cliente.getCpf());
        executar("limpar preferencias de", "delete from ClientePreferencia where fkCPFCliente = ?", cpf);
        for (String preferencia : cliente.getPreferencias()) {
            executar("gravar preferencia de", """
                    insert into ClientePreferencia (fkCPFCliente, preferencia) values (?, ?)
                    on conflict do nothing
                    """, cpf, preferencia);
        }
    }

    private static LocalDateTime dataDeCadastro(Cliente cliente) {
        return cliente.getDataCadastro() == null ? LocalDateTime.now() : cliente.getDataCadastro();
    }
}
