package br.com.conde.lsm;

import java.util.ArrayList;
import java.util.List;

import br.com.conde.lsm.Juiz.Operacao;
import br.com.conde.lsm.Loja.Politica;

/**
 * As MEDIDAS, em bytes e em arquivos, nunca em segundos.
 *
 * <p>Segundo nao e medida: muda com a maquina, com o disco e com o humor do coletor de
 * lixo, e a integracao continua roda em tres sistemas.
 *
 * <p>Toda tabela aqui mede uma das TRES AMPLIFICACOES, e o assunto do repositorio e que
 * nao da para melhorar as tres ao mesmo tempo.
 */
public final class Medidas {

    private Medidas() {
    }

    /** Quantas operacoes de escrita cada roteiro faz. */
    public static final int ESCRITAS = 20_000;

    /** Quantas chaves cabem na memoria antes de ela virar arquivo. */
    public static final int MEMORIA = 200;

    /** Quantos arquivos cabem no nivel zero antes de compactar. */
    public static final int ARQUIVOS_POR_NIVEL = 4;

    /** Quantas leituras as tabelas de leitura fazem. */
    public static final int LEITURAS = 5_000;

    // ───────────────────── 1. a correcao ─────────────────────

    public record LinhaCorrecao(String roteiro, String politica, boolean comFiltro,
                                int operacoes, int leituras, int divergencias,
                                int arquivos, int niveis) {
    }

    /**
     * TODA LEITURA BATE COM O ESPELHO, em toda forma de roteiro e nas duas politicas.
     *
     * <p>A conferencia e de TODAS as chaves que ja existiram, no fim, e nao de uma
     * amostra. As chaves apagadas sao justamente as que a arvore erra quando a ordem de
     * mesclagem esta trocada, e elas nunca seriam lidas por um roteiro que so le o que
     * acabou de escrever.
     */
    public static List<LinhaCorrecao> aCorrecao() {
        List<LinhaCorrecao> linhas = new ArrayList<>();

        Roteiros.todas(5_000, 1).forEach((nome, escrita) -> {
            for (Politica politica : Politica.values())
                for (boolean comFiltro : new boolean[] {false, true}) {
                    Contador contador = new Contador();
                    Loja loja = new Loja(MEMORIA, ARQUIVOS_POR_NIVEL, politica, comFiltro, contador);

                    List<Operacao> roteiro = Roteiros.juntar(escrita,
                            Roteiros.leituraDeExistentes(1_000, 1_000, 2));

                    Juiz.Veredito veredito = Juiz.julgar(loja, roteiro, true);

                    linhas.add(new LinhaCorrecao(nome, nomeDe(politica), comFiltro,
                            veredito.operacoes(), veredito.leituras(), veredito.divergencias(),
                            veredito.arquivos(), veredito.niveis()));
                }
        });

        return linhas;
    }

    // ───────────────────── 2. a escrita ─────────────────────

    public record LinhaEscrita(String roteiro, String politica, long bytesDoUsuario,
                               long bytesNoDisco, double amplificacao, long compactacoes,
                               int niveis) {
    }

    /**
     * A AMPLIFICACAO DE ESCRITA: quantos bytes foram parar no disco por byte que o
     * usuario mandou gravar.
     *
     * <p>Compactar e reescrever. Um dado que desce cinco niveis foi escrito seis vezes.
     *
     * <p>A compactacao POR NIVEL reescreve muito mais, porque ela mexe num arquivo de cada
     * vez e tem de mesclar com todos os de baixo que tocam a faixa dele. A POR TAMANHO
     * junta tudo de uma vez e desce, e escreve bem menos.
     */
    public static List<LinhaEscrita> aEscrita() {
        List<LinhaEscrita> linhas = new ArrayList<>();

        Roteiros.todas(ESCRITAS, 1).forEach((nome, escrita) -> {
            for (Politica politica : Politica.values()) {
                Contador contador = new Contador();
                Loja loja = new Loja(MEMORIA, ARQUIVOS_POR_NIVEL, politica, true, contador);

                for (Operacao operacao : escrita)
                    if (operacao.tipo() == Operacao.Tipo.APAGA) loja.apaga(operacao.chave());
                    else loja.poe(operacao.chave(), operacao.valor());

                loja.descarregar();

                linhas.add(new LinhaEscrita(nome, nomeDe(politica), contador.bytesDoUsuario(),
                        contador.bytesEscritos(), contador.amplificacaoDeEscrita(),
                        contador.compactacoes(), loja.quantosNiveis()));
            }
        });

        return linhas;
    }

    // ───────────────────── 3. a leitura ─────────────────────

    public record LinhaLeitura(String politica, boolean comFiltro, String oQueLe,
                               int arquivos, double arquivosPorLeitura,
                               double buscasPorLeitura, long buscasEmVao,
                               long descartesPeloFiltro) {
    }

    /**
     * A AMPLIFICACAO DE LEITURA: quantos arquivos uma busca olha.
     *
     * <p>A assimetria e o assunto. Achar uma chave que EXISTE custa olhar os arquivos ate
     * ela aparecer, e ela costuma aparecer cedo porque o mais recente esta mais em cima.
     * Descobrir que uma chave NAO existe custa olhar TODOS, ate o ultimo, para ter certeza.
     *
     * <p>A coluna das BUSCAS por leitura e a que o filtro de Bloom muda. A dos ARQUIVOS
     * conta quantos foram considerados, e o filtro nao diminui isso: ele diminui quantos
     * foram abertos de verdade.
     */
    public static List<LinhaLeitura> aLeitura() {
        List<LinhaLeitura> linhas = new ArrayList<>();
        List<Operacao> escrita = Roteiros.repetidas(ESCRITAS, 2_000, 1);

        for (Politica politica : Politica.values())
            for (boolean comFiltro : new boolean[] {false, true}) {
                for (String oQueLe : new String[] {"existentes", "ausentes"}) {
                    Contador contador = new Contador();
                    Loja loja = new Loja(MEMORIA, ARQUIVOS_POR_NIVEL, politica, comFiltro, contador);

                    for (Operacao operacao : escrita) loja.poe(operacao.chave(), operacao.valor());

                    loja.descarregar();

                    int arquivos = loja.quantosArquivos();

                    contador.zerar();

                    List<Operacao> leituras = oQueLe.equals("existentes")
                            ? Roteiros.leituraDeExistentes(LEITURAS, 2_000, 3)
                            : Roteiros.leituraDeAusentes(LEITURAS, 2_000, 3);

                    for (Operacao operacao : leituras) loja.pega(operacao.chave());

                    linhas.add(new LinhaLeitura(nomeDe(politica), comFiltro, oQueLe, arquivos,
                            contador.arquivosPorLeitura(), contador.buscasPorLeitura(),
                            contador.buscasEmVao(), contador.descartesPeloFiltro()));
                }
            }

        return linhas;
    }

    // ───────────────────── 4. o espaco ─────────────────────

    public record LinhaEspaco(String roteiro, String politica, long bytesVivos,
                              long bytesNoDisco, double amplificacao, long lapides,
                              long entradasNoDisco, int chavesVivas) {
    }

    /**
     * A AMPLIFICACAO DE ESPACO: quantos bytes o disco ocupa por byte que ainda vale.
     *
     * <p>Valores velhos e lapides ficam la ate alguem compactar. Num roteiro de chaves
     * sempre novas nao ha valor velho nenhum e a amplificacao e um; num roteiro que
     * reescreve cem chaves vinte mil vezes, ha muito.
     *
     * <p>A coluna das LAPIDES e a mais contraintuitiva. Apagar uma chave numa arvore de
     * mesclagem e ESCREVER: a chave apagada passa a ocupar MAIS lugar do que ocupava, e
     * continua ocupando ate a compactacao chegar ao ultimo nivel.
     */
    public static List<LinhaEspaco> oEspaco() {
        List<LinhaEspaco> linhas = new ArrayList<>();

        Roteiros.todas(ESCRITAS, 1).forEach((nome, escrita) -> {
            for (Politica politica : Politica.values()) {
                Contador contador = new Contador();
                Loja loja = new Loja(MEMORIA, ARQUIVOS_POR_NIVEL, politica, true, contador);

                Juiz.Veredito veredito = Juiz.julgar(loja, escrita, false);

                loja.descarregar();

                long bytesVivos = veredito.bytesVivos();

                linhas.add(new LinhaEspaco(nome, nomeDe(politica), bytesVivos,
                        loja.bytesNoDisco(),
                        bytesVivos == 0 ? 0 : (double) loja.bytesNoDisco() / bytesVivos,
                        loja.lapides(), loja.entradasNoDisco(),
                        Juiz.chavesDistintas(escrita)));
            }
        });

        return linhas;
    }

    // ───────────────────── 5. as tres juntas ─────────────────────

    public record LinhaTroca(String politica, boolean comFiltro, double escrita,
                             double leituraDeAusentes, double espaco, int arquivos,
                             int niveis, int cruzamentos) {
    }

    /**
     * AS TRES AMPLIFICACOES LADO A LADO, no mesmo roteiro.
     *
     * <p>E a tabela inteira do repositorio em tres numeros. As duas politicas nao sao
     * melhor e pior: cada uma e melhor em duas colunas e pior na terceira.
     *
     * <p>A ultima coluna explica a do meio. Na compactacao por nivel, os arquivos de um
     * mesmo nivel nao tem faixas que se cruzam, e por isso uma leitura olha no maximo um
     * arquivo por nivel. Na por tamanho elas se cruzam, e a leitura tem de olhar todos.
     *
     * <p>E ha as duas metades da tabela, COM e SEM filtro de Bloom, porque elas contam
     * historias diferentes. Sem filtro, a diferenca de leitura entre as duas politicas e
     * de quase duas vezes. Com filtro, ela quase some, e a escolha passa a ser so entre
     * escrita e espaco.
     */
    public static List<LinhaTroca> aTroca() {
        List<LinhaTroca> linhas = new ArrayList<>();
        List<Operacao> escrita = Roteiros.repetidas(ESCRITAS, 2_000, 1);

        for (boolean comFiltro : new boolean[] {false, true})
            for (Politica politica : Politica.values()) {
                Contador contador = new Contador();
                Loja loja = new Loja(MEMORIA, ARQUIVOS_POR_NIVEL, politica, comFiltro, contador);

                Juiz.Veredito veredito = Juiz.julgar(loja, escrita, false);

                loja.descarregar();

                double amplificacaoDeEscrita = contador.amplificacaoDeEscrita();

                contador.zerar();

                for (Operacao operacao : Roteiros.leituraDeAusentes(LEITURAS, 2_000, 3))
                    loja.pega(operacao.chave());

                long bytesVivos = veredito.bytesVivos();

                linhas.add(new LinhaTroca(nomeDe(politica), comFiltro, amplificacaoDeEscrita,
                        contador.buscasPorLeitura(),
                        bytesVivos == 0 ? 0 : (double) loja.bytesNoDisco() / bytesVivos,
                        loja.quantosArquivos(), loja.quantosNiveis(),
                        loja.cruzamentosNoMesmoNivel()));
            }

        return linhas;
    }

    static String nomeDe(Politica politica) {
        return politica == Politica.POR_NIVEL ? "por nivel" : "por tamanho";
    }
}
