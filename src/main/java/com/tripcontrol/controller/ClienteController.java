package com.tripcontrol.controller;

import com.tripcontrol.controller.dto.DadosCliente;
import com.tripcontrol.controller.dto.ResumoReserva;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.Reserva;
import com.tripcontrol.repository.ClienteRepository;
import com.tripcontrol.repository.PacoteRepository;
import com.tripcontrol.repository.ReservaRepository;
import com.tripcontrol.util.Formatadores;
import com.tripcontrol.util.ValidadorCpf;
import com.tripcontrol.util.ValidadorTelefone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Regras de aplicacao do UC02 (Cadastrar Clientes).
 *
 * <p>O historico de viagens exibido na ficha nao e um campo digitavel: ele e
 * derivado das reservas do cliente, o que evita informacao duplicada e sempre
 * reflete o estado real do sistema.</p>
 */
public class ClienteController {

    private static final Pattern FORMATO_EMAIL =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.-]{2,}$");

    private final ClienteRepository clienteRepository;
    private final ReservaRepository reservaRepository;
    private final PacoteRepository pacoteRepository;

    public ClienteController(ClienteRepository clienteRepository,
                             ReservaRepository reservaRepository,
                             PacoteRepository pacoteRepository) {
        this.clienteRepository = clienteRepository;
        this.reservaRepository = reservaRepository;
        this.pacoteRepository = pacoteRepository;
    }

    /**
     * Cadastra um cliente (UC02, passos 4 e 5).
     *
     * @return SUCESSO com o cliente gravado; ERRO_VALIDACAO na situacao FA01;
     *         DUPLICIDADE, com o cliente ja cadastrado, na situacao FA02
     */
    public Resultado<Cliente> cadastrar(DadosCliente dados) {
        List<ErroValidacao> erros = validarCampos(dados);
        if (!erros.isEmpty()) {
            return Resultado.erroValidacao(erros);
        }

        String cpf = ValidadorCpf.apenasDigitos(dados.cpf());

        // FA02 - CPF ja cadastrado.
        Optional<Cliente> existente = clienteRepository.buscarPorCpf(cpf);
        if (existente.isPresent()) {
            return Resultado.duplicidade(existente.get(),
                    "O CPF " + ValidadorCpf.formatar(cpf) + " ja pertence ao cliente "
                            + existente.get().getNome() + ". Deseja abrir o cadastro existente?");
        }

        Cliente cliente = new Cliente();
        aplicarDados(cliente, dados, cpf);
        clienteRepository.salvar(cliente);

        return Resultado.sucesso(cliente, "Cliente " + cliente.getNome() + " cadastrado com sucesso.");
    }

    /** Atualiza a ficha de um cliente ja existente (caminho oferecido no FA02). */
    public Resultado<Cliente> atualizar(Long clienteId, DadosCliente dados) {
        Optional<Cliente> encontrado = clienteRepository.buscarPorId(clienteId);
        if (encontrado.isEmpty()) {
            return Resultado.naoEncontrado("Cliente nao localizado para edicao.");
        }

        List<ErroValidacao> erros = validarCampos(dados);
        if (!erros.isEmpty()) {
            return Resultado.erroValidacao(erros);
        }

        String cpf = ValidadorCpf.apenasDigitos(dados.cpf());
        Optional<Cliente> duplicado = clienteRepository.buscarPorCpf(cpf);
        if (duplicado.isPresent() && !duplicado.get().getId().equals(clienteId)) {
            return Resultado.duplicidade(duplicado.get(),
                    "Esse CPF ja pertence ao cliente " + duplicado.get().getNome() + ".");
        }

        Cliente cliente = encontrado.get();
        aplicarDados(cliente, dados, cpf);
        clienteRepository.atualizar(cliente);
        return Resultado.sucesso(cliente, "Cadastro de " + cliente.getNome() + " atualizado com sucesso.");
    }

    public List<Cliente> listar() {
        return clienteRepository.buscarTodos().stream()
                .sorted(Comparator.comparing(Cliente::getNome, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    public Optional<Cliente> buscarPorId(Long id) {
        return clienteRepository.buscarPorId(id);
    }

    public Optional<Cliente> buscarPorCpf(String cpf) {
        return clienteRepository.buscarPorCpf(ValidadorCpf.apenasDigitos(cpf));
    }

    public List<Cliente> buscarPorNome(String trecho) {
        return clienteRepository.buscarPorNome(trecho);
    }

    /**
     * Historico de viagens do cliente (UC02, passo 2), derivado das reservas.
     */
    public List<ResumoReserva> historicoDeViagens(Long clienteId) {
        return reservaRepository.buscarPorCliente(clienteId).stream()
                .sorted(Comparator.comparing(Reserva::getDataInicio,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(reserva -> {
                    Pacote pacote = pacoteRepository.buscarPorId(reserva.getPacoteId()).orElse(null);
                    Cliente cliente = clienteRepository.buscarPorId(clienteId).orElse(null);
                    return new ResumoReserva(reserva, cliente, pacote);
                })
                .toList();
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /** Validacoes do FA01: obrigatorios em branco e formatos invalidos. */
    private List<ErroValidacao> validarCampos(DadosCliente dados) {
        List<ErroValidacao> erros = new ArrayList<>();

        if (Formatadores.textoOuNulo(dados.nome()) == null) {
            erros.add(new ErroValidacao("nome", "Informe o nome completo do cliente."));
        }

        String cpf = Formatadores.textoOuNulo(dados.cpf());
        if (cpf == null) {
            erros.add(new ErroValidacao("cpf", "Informe o CPF do cliente."));
        } else if (!ValidadorCpf.isValido(cpf)) {
            erros.add(new ErroValidacao("cpf", "CPF invalido. Use o formato 000.000.000-00."));
        }

        String telefone = Formatadores.textoOuNulo(dados.telefone());
        if (telefone == null) {
            erros.add(new ErroValidacao("telefone", "Informe o telefone de contato."));
        } else if (!ValidadorTelefone.isValido(telefone)) {
            erros.add(new ErroValidacao("telefone", "Telefone invalido. Use (00) 00000-0000."));
        }

        String email = Formatadores.textoOuNulo(dados.email());
        if (email != null && !FORMATO_EMAIL.matcher(email).matches()) {
            erros.add(new ErroValidacao("email", "E-mail invalido."));
        }

        return erros;
    }

    private void aplicarDados(Cliente cliente, DadosCliente dados, String cpfSomenteDigitos) {
        cliente.setNome(dados.nome().trim());
        cliente.setCpf(cpfSomenteDigitos);
        cliente.setTelefone(ValidadorTelefone.apenasDigitos(dados.telefone()));
        cliente.setEmail(Formatadores.textoOuNulo(dados.email()));
        cliente.setEndereco(Formatadores.textoOuNulo(dados.endereco()));
        cliente.definirPreferencias(dados.preferencias());
    }
}
