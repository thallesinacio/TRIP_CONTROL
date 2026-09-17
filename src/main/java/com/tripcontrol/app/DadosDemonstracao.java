package com.tripcontrol.app;

import com.tripcontrol.controller.dto.DadosCliente;
import com.tripcontrol.controller.dto.DadosPacote;
import com.tripcontrol.controller.dto.DadosReserva;
import com.tripcontrol.model.Cliente;
import com.tripcontrol.model.Pacote;
import com.tripcontrol.model.PerfilAcesso;
import com.tripcontrol.util.Formatadores;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Carga inicial usada apenas para demonstrar as telas enquanto os dados vivem
 * em memoria. Quando o banco entrar, este carregamento deixa de ser chamado.
 */
public final class DadosDemonstracao {

    /** Credenciais da conta de demonstracao exibidas na tela de login. */
    public static final String EMAIL_DEMONSTRACAO = "ana.silva@tripcontrol.com";
    public static final String SENHA_DEMONSTRACAO = "tripcontrol";

    private DadosDemonstracao() {
    }

    public static void carregar(ContextoAplicacao contexto) {
        contexto.getAutenticacaoController().cadastrarUsuario(
                "Ana Silva", EMAIL_DEMONSTRACAO, SENHA_DEMONSTRACAO, PerfilAcesso.FUNCIONARIO);

        LocalDate hoje = LocalDate.now();

        Pacote gramado = criarPacote(contexto, "Gramado & Canela, RS",
                hoje.plusMonths(2), hoje.plusMonths(2).plusDays(7),
                "Roteiro cultural com passeios em vinicolas e jantares tipicos.",
                "3490,00", "30",
                "Dia 1: Chegada e check-in\nDia 2: Tour guiado pelos pontos historicos\nDia 3: Dia livre");

        Pacote noronha = criarPacote(contexto, "Fernando de Noronha, PE",
                hoje.plusMonths(1), hoje.plusMonths(1).plusDays(6),
                "Ilha, mergulho e trilhas guiadas.",
                "4800,00", "20",
                "Dia 1: Chegada\nDia 2: Mergulho na Baia do Sancho\nDia 3: Trilha do Atalaia");

        criarPacote(contexto, "Santiago, Chile",
                hoje.plusMonths(3), hoje.plusMonths(3).plusDays(5),
                "City tour, cordilheira e vinicolas.",
                "5200,00", "25",
                "Dia 1: Chegada\nDia 2: City tour\nDia 3: Valle Nevado");

        // Pacote pequeno que sera totalmente ocupado mais abaixo: serve para a
        // situacao "Lotado" aparecer na tela de controle de vagas (UC04).
        Pacote reveillon = criarPacote(contexto, "Reveillon Copacabana, RJ",
                hoje.plusMonths(4), hoje.plusMonths(4).plusDays(4),
                "Hotel na orla com vista para a queima de fogos.",
                "2900,00", "6",
                "Dia 1: Check-in\nDia 2: City tour\nDia 3: Reveillon na praia");

        // Pacote ja encerrado, gravado direto no repositorio porque o UC01 (com razao)
        // recusa cadastro com data de inicio no passado. E apenas massa de demonstracao:
        // serve para a situacao "Encerrado" aparecer na tela de vagas.
        criarPacoteEncerrado(contexto, "Machu Picchu, Peru",
                hoje.minusMonths(2), hoje.minusMonths(2).plusDays(6),
                "Trem panoramico e trilha guiada.", new BigDecimal("6100.00"), 22);

        Cliente carlos = criarCliente(contexto, "Carlos Eduardo de Souza", "529.982.247-25",
                "(87) 99999-0000", "carlos.souza@email.com", List.of("Praia", "Ecoturismo"));
        Cliente mariana = criarCliente(contexto, "Mariana Silva de Oliveira", "111.444.777-35",
                "(81) 98888-1234", "mariana.oliveira@email.com", List.of("Viagens Historicas", "Hoteis 5 Estrelas"));

        if (gramado != null && carlos != null) {
            contexto.getReservaController().registrar(new DadosReserva(
                    carlos.getId(), gramado.getId(), "2",
                    Formatadores.formatarData(gramado.getDataInicio()),
                    Formatadores.formatarData(gramado.getDataFim()),
                    "Preferencia por quarto com vista."));
        }
        if (noronha != null && mariana != null) {
            contexto.getReservaController().registrar(new DadosReserva(
                    mariana.getId(), noronha.getId(), "2",
                    Formatadores.formatarData(noronha.getDataInicio()),
                    Formatadores.formatarData(noronha.getDataFim()),
                    null));
        }

        // Ocupa todas as vagas do pacote de Reveillon (situacao "Lotado" no UC04).
        if (reveillon != null && carlos != null) {
            contexto.getReservaController().registrar(new DadosReserva(
                    carlos.getId(), reveillon.getId(), "6",
                    Formatadores.formatarData(reveillon.getDataInicio()),
                    Formatadores.formatarData(reveillon.getDataFim()),
                    "Grupo familiar."));
        }
    }

    /**
     * Grava um pacote com periodo ja encerrado diretamente pelo repositorio.
     * Nao passa pelo {@code PacoteController} de proposito: o UC01 recusa datas
     * de inicio no passado, e essa recusa deve continuar valendo no cadastro real.
     */
    private static void criarPacoteEncerrado(ContextoAplicacao contexto, String destino,
                                             LocalDate inicio, LocalDate fim, String descricao,
                                             BigDecimal preco, int capacidade) {
        Pacote pacote = new Pacote(destino, inicio, fim, descricao, preco, capacidade,
                "Roteiro concluido.");
        pacote.setCodigo(contexto.getPacoteRepository().proximoCodigo());
        contexto.getPacoteRepository().salvar(pacote);
    }

    private static Pacote criarPacote(ContextoAplicacao contexto, String destino, LocalDate inicio,
                                      LocalDate fim, String descricao, String preco,
                                      String capacidade, String roteiro) {
        return contexto.getPacoteController().cadastrar(new DadosPacote(
                        destino,
                        Formatadores.formatarData(inicio),
                        Formatadores.formatarData(fim),
                        descricao, preco, capacidade, roteiro))
                .getDado().orElse(null);
    }

    private static Cliente criarCliente(ContextoAplicacao contexto, String nome, String cpf, String telefone,
                                        String email, List<String> preferencias) {
        return contexto.getClienteController().cadastrar(new DadosCliente(
                        nome, cpf, telefone, email, "Petrolina - PE", preferencias))
                .getDado().orElse(null);
    }
}
