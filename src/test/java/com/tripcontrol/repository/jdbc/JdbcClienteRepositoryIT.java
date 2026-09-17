package com.tripcontrol.repository.jdbc;

import com.tripcontrol.model.Cliente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Persistencia de clientes e das suas preferencias (UC02). */
class JdbcClienteRepositoryIT extends RepositorioJdbcIT {

    private final JdbcClienteRepository repositorio = new JdbcClienteRepository();

    private Cliente novoCliente(String cpf, String nome) {
        Cliente cliente = new Cliente(nome, cpf, "87999990000");
        cliente.setEmail(nome.toLowerCase().replace(" ", ".") + "@email.com");
        cliente.setEndereco("Petrolina - PE");
        cliente.definirPreferencias(List.of("Praia", "Ecoturismo"));
        return cliente;
    }

    @Test
    @DisplayName("Cliente gravado volta com contato, endereco e preferencias")
    void gravaERecupera() {
        Cliente gravado = repositorio.salvar(novoCliente("52998224725", "Carlos Eduardo"));
        assertNotNull(gravado.getId());

        Cliente lido = repositorio.buscarPorId(gravado.getId()).orElseThrow();
        assertEquals("52998224725", lido.getCpf());
        assertEquals("Carlos Eduardo", lido.getNome());
        assertEquals("87999990000", lido.getTelefone());
        assertEquals("Petrolina - PE", lido.getEndereco());
        assertEquals(2, lido.getPreferencias().size());
        assertTrue(lido.getPreferencias().contains("Praia"));
    }

    @Test
    @DisplayName("Busca por CPF aceita valor com mascara")
    void buscaPorCpfComMascara() {
        repositorio.salvar(novoCliente("11144477735", "Mariana Silva"));

        assertTrue(repositorio.buscarPorCpf("111.444.777-35").isPresent());
    }

    @Test
    @DisplayName("Atualizar substitui a lista de preferencias")
    void atualizaPreferencias() {
        Cliente cliente = repositorio.salvar(novoCliente("52998224725", "Carlos Eduardo"));

        cliente.definirPreferencias(List.of("Hoteis 5 Estrelas"));
        repositorio.atualizar(cliente);

        Cliente lido = repositorio.buscarPorId(cliente.getId()).orElseThrow();
        assertEquals(1, lido.getPreferencias().size());
        assertEquals("Hoteis 5 Estrelas", lido.getPreferencias().get(0));
    }

    @Test
    @DisplayName("Busca por nome e por preferencia localizam o cliente")
    void buscaPorNomeEPreferencia() {
        Cliente gravado = repositorio.salvar(novoCliente("11144477735", "Mariana Silva"));

        assertTrue(repositorio.buscarPorNome("mariana").stream()
                .anyMatch(cliente -> gravado.getId().equals(cliente.getId())));
        assertTrue(repositorio.buscarPorPreferencia("ecoturismo").stream()
                .anyMatch(cliente -> gravado.getId().equals(cliente.getId())));
    }
}
