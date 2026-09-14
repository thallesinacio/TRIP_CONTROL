package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.DadosCliente;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.repository.ClienteRepository;
import com.tripcontrol.repository.memory.InMemoryClienteRepository;
import com.tripcontrol.repository.memory.InMemoryPacoteRepository;
import com.tripcontrol.repository.memory.InMemoryReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cobre o fluxo principal e os fluxos alternativos FA01 e FA02 do UC02. */
class ClienteControllerTest {

    private ClienteRepository clienteRepository;
    private ClienteController controller;

    @BeforeEach
    void prepararCenario() {
        clienteRepository = new InMemoryClienteRepository();
        controller = new ClienteController(clienteRepository,
                new InMemoryReservaRepository(), new InMemoryPacoteRepository());
    }

    private DadosCliente dadosValidos() {
        return new DadosCliente("Carlos Eduardo de Souza", "529.982.247-25", "(87) 99999-0000",
                "carlos@email.com", "Petrolina - PE", List.of("Praia", "Ecoturismo"));
    }

    @Test
    @DisplayName("Fluxo principal: cliente valido e gravado com CPF normalizado")
    void cadastraClienteValido() {
        Resultado<Cliente> resultado = controller.cadastrar(dadosValidos());

        assertTrue(resultado.isSucesso());
        Cliente cliente = resultado.getDado().orElseThrow();
        assertEquals("52998224725", cliente.getCpf());
        assertEquals("87999990000", cliente.getTelefone());
        assertEquals(2, cliente.getPreferencias().size());
    }

    @Test
    @DisplayName("FA01: nome vazio, CPF invalido e telefone invalido sao apontados")
    void recusaDadosInvalidos() {
        Resultado<Cliente> resultado = controller.cadastrar(new DadosCliente(
                "  ", "123.456.789-00", "9999", "email-invalido", null, List.of()));

        assertEquals(StatusResultado.ERRO_VALIDACAO, resultado.getStatus());
        assertTrue(resultado.mensagemDoCampo("nome").isPresent());
        assertTrue(resultado.mensagemDoCampo("cpf").isPresent());
        assertTrue(resultado.mensagemDoCampo("telefone").isPresent());
        assertTrue(resultado.mensagemDoCampo("email").isPresent());
        assertEquals(0L, clienteRepository.contar());
    }

    @Test
    @DisplayName("FA02: CPF ja cadastrado devolve o cliente existente")
    void detectaCpfDuplicado() {
        controller.cadastrar(dadosValidos());

        Resultado<Cliente> segundo = controller.cadastrar(new DadosCliente(
                "Outro Nome", "52998224725", "(81) 98888-1234", null, null, List.of()));

        assertEquals(StatusResultado.DUPLICIDADE, segundo.getStatus());
        assertEquals("Carlos Eduardo de Souza", segundo.getDado().orElseThrow().getNome());
        assertEquals(1L, clienteRepository.contar());
    }

    @Test
    @DisplayName("Edicao mantem o mesmo registro e nao acusa duplicidade do proprio CPF")
    void atualizaClienteExistente() {
        Cliente cliente = controller.cadastrar(dadosValidos()).getDado().orElseThrow();

        Resultado<Cliente> resultado = controller.atualizar(cliente.getId(), new DadosCliente(
                "Carlos E. de Souza", "529.982.247-25", "(87) 99999-0001",
                null, "Juazeiro - BA", List.of("Praia")));

        assertTrue(resultado.isSucesso());
        assertEquals("Carlos E. de Souza", resultado.getDado().orElseThrow().getNome());
        assertEquals(1L, clienteRepository.contar());
    }
}
