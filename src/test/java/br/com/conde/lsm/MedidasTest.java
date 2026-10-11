package br.com.conde.lsm;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Os testes das TABELAS que o medidor imprime.
 *
 * <p>Toda frase do README que e um numero esta aqui como afirmacao. Texto nao quebra
 * quando deixa de ser verdade; teste quebra.
 */
class MedidasTest {

    private static final List<Medidas.LinhaCorrecao> CORRECAO = Medidas.aCorrecao();
    private static final List<Medidas.LinhaEscrita> ESCRITA = Medidas.aEscrita();
    private static final List<Medidas.LinhaLeitura> LEITURA = Medidas.aLeitura();
    private static final List<Medidas.LinhaEspaco> ESPACO = Medidas.oEspaco();
    private static final List<Medidas.LinhaTroca> TROCA = Medidas.aTroca();

    // ───────────────────── a correcao ─────────────────────

    @Test
    @DisplayName("as vinte combinacoes de roteiro, politica e filtro batem todas com o espelho")
    void aTabelaDaCorrecao() {
        assertThat(CORRECAO).hasSize(5 * 2 * 2);
        assertThat(CORRECAO).allSatisfy(l -> {
            assertThat(l.divergencias())
                    .as("%s, %s, filtro %s", l.roteiro(), l.politica(), l.comFiltro()).isZero();
            assertThat(l.leituras()).isPositive();
        });

        // o filtro nao muda a resposta, so o custo de chegar nela
        for (Medidas.LinhaCorrecao com : CORRECAO) {
            if (!com.comFiltro()) continue;

            Medidas.LinhaCorrecao sem = acharCorrecao(com.roteiro(), com.politica(), false);

            assertThat(com.arquivos()).as("%s, %s", com.roteiro(), com.politica())
                    .isEqualTo(sem.arquivos());
        }
    }

    // ───────────────────── a escrita ─────────────────────

    /**
     * O ACHADO DA ESCRITA: mesmas chaves, mesmos bytes, so a ORDEM muda, e a amplificacao
     * de escrita vai de 2,5 para 47.
     */
    @Test
    @DisplayName("por nivel, escrever em ordem custa 2,5 e escrever embaralhado custa 47")
    void aOrdemDasChavesDecideAEscrita() {
        Medidas.LinhaEscrita emOrdem = acharEscrita("em ordem", "por nivel");
        Medidas.LinhaEscrita embaralhado = acharEscrita("sempre novas", "por nivel");

        assertThat(emOrdem.bytesDoUsuario()).isEqualTo(embaralhado.bytesDoUsuario());

        assertThat(emOrdem.amplificacao()).isLessThan(3.0);
        assertThat(embaralhado.amplificacao()).isGreaterThan(40.0);
        assertThat(embaralhado.amplificacao() / emOrdem.amplificacao()).isGreaterThan(15.0);
    }

    /**
     * E A COMPACTACAO POR TAMANHO nao se importa com a ordem: ela da 3,00 nos dois.
     */
    @Test
    @DisplayName("por tamanho, a ordem das chaves nao muda a amplificacao de escrita")
    void porTamanhoAOrdemNaoImporta() {
        assertThat(acharEscrita("em ordem", "por tamanho").amplificacao())
                .isCloseTo(acharEscrita("sempre novas", "por tamanho").amplificacao(), within(0.01));
    }

    @Test
    @DisplayName("por tamanho escreve menos que por nivel em todo roteiro que compacta")
    void porTamanhoEscreveMenos() {
        for (Medidas.LinhaEscrita porNivel : ESCRITA) {
            if (!porNivel.politica().equals("por nivel") || porNivel.compactacoes() == 0) continue;

            if (porNivel.roteiro().equals("em ordem")) continue;

            assertThat(acharEscrita(porNivel.roteiro(), "por tamanho").amplificacao())
                    .as("em %s", porNivel.roteiro()).isLessThan(porNivel.amplificacao());
        }
    }

    /**
     * O caso degenerado: cem chaves cabem na memoria e nunca viram arquivo ate o fim. A
     * amplificacao de escrita fica em 0,01, que e menos de um.
     */
    @Test
    @DisplayName("quando o conjunto de chaves cabe na memoria, nada e compactado")
    void oConjuntoQueCabeNaMemoria() {
        for (String politica : new String[] {"por nivel", "por tamanho"}) {
            Medidas.LinhaEscrita l = acharEscrita("repetidas em 100", politica);

            assertThat(l.compactacoes()).as(politica).isZero();
            assertThat(l.niveis()).as(politica).isEqualTo(1);
            assertThat(l.amplificacao()).as(politica).isLessThan(0.1);
        }
    }

    // ───────────────────── a leitura ─────────────────────

    /**
     * A ASSIMETRIA: achar uma chave que existe custa olhar os arquivos ate ela aparecer;
     * descobrir que uma chave NAO existe custa olhar TODOS.
     */
    @Test
    @DisplayName("sem filtro, uma leitura de chave ausente olha todos os arquivos e uma de existente nao")
    void aTabelaDaLeitura() {
        assertThat(LEITURA).hasSize(2 * 2 * 2);

        for (String politica : new String[] {"por nivel", "por tamanho"}) {
            Medidas.LinhaLeitura ausentes = acharLeitura(politica, false, "ausentes");
            Medidas.LinhaLeitura existentes = acharLeitura(politica, false, "existentes");

            assertThat(ausentes.arquivosPorLeitura()).as(politica)
                    .isCloseTo(ausentes.arquivos(), within(0.01));

            assertThat(existentes.arquivosPorLeitura()).as(politica)
                    .isLessThan(existentes.arquivos());
        }
    }

    /**
     * O FILTRO DE BLOOM corta a busca em vao a quase nada, e e nela que ele mexe: a coluna
     * dos ARQUIVOS considerados nao muda.
     */
    @Test
    @DisplayName("o filtro corta a busca de uma chave ausente de quase cinco para quase zero")
    void oFiltroCortaABuscaEmVao() {
        for (String politica : new String[] {"por nivel", "por tamanho"}) {
            Medidas.LinhaLeitura sem = acharLeitura(politica, false, "ausentes");
            Medidas.LinhaLeitura com = acharLeitura(politica, true, "ausentes");

            assertThat(sem.buscasPorLeitura()).as(politica).isGreaterThan(4.0);
            assertThat(com.buscasPorLeitura()).as(politica).isLessThan(0.2);

            // e os arquivos considerados sao exatamente os mesmos
            assertThat(com.arquivosPorLeitura()).as(politica)
                    .isCloseTo(sem.arquivosPorLeitura(), within(0.01));

            assertThat(com.descartesPeloFiltro()).as(politica).isPositive();
            assertThat(sem.descartesPeloFiltro()).as(politica).isZero();
        }
    }

    // ───────────────────── o espaco ─────────────────────

    /**
     * Num roteiro de chaves SEMPRE NOVAS nao ha valor velho nenhum e a amplificacao de
     * espaco e exatamente um, nas duas politicas.
     */
    @Test
    @DisplayName("sem reescrita nao ha amplificacao de espaco nenhuma")
    void aTabelaDoEspaco() {
        assertThat(ESPACO).hasSize(5 * 2);

        for (String roteiro : new String[] {"sempre novas", "em ordem"})
            for (String politica : new String[] {"por nivel", "por tamanho"}) {
                Medidas.LinhaEspaco l = acharEspaco(roteiro, politica);

                assertThat(l.amplificacao()).as("%s, %s", roteiro, politica).isEqualTo(1.0);
                assertThat(l.lapides()).as("%s, %s", roteiro, politica).isZero();
                assertThat(l.entradasNoDisco()).as("%s, %s", roteiro, politica)
                        .isEqualTo(l.chavesVivas());
            }
    }

    /**
     * E COM REESCRITA a compactacao por tamanho ocupa quase TRES VEZES o que a por nivel
     * ocupa, com os mesmos dados vivos.
     */
    @Test
    @DisplayName("por tamanho ocupa quase tres vezes mais disco que por nivel, com os mesmos dados vivos")
    void porTamanhoOcupaMais() {
        Medidas.LinhaEspaco porNivel = acharEspaco("repetidas em 1000", "por nivel");
        Medidas.LinhaEspaco porTamanho = acharEspaco("repetidas em 1000", "por tamanho");

        assertThat(porNivel.bytesVivos()).isEqualTo(porTamanho.bytesVivos());
        assertThat(porNivel.amplificacao()).isLessThan(2.0);
        assertThat(porTamanho.amplificacao()).isGreaterThan(4.0);
        assertThat(porTamanho.bytesNoDisco()).isGreaterThan(2 * porNivel.bytesNoDisco());
    }

    /**
     * AS LAPIDES: a chave apagada continua ocupando lugar, e por tamanho ela ocupa cinco
     * vezes mais lugar do que por nivel.
     */
    @Test
    @DisplayName("as lapides sobrevivem a compactacao, e por tamanho sobrevivem cinco vezes mais")
    void asLapidesSobrevivem() {
        Medidas.LinhaEspaco porNivel = acharEspaco("com apagamentos", "por nivel");
        Medidas.LinhaEspaco porTamanho = acharEspaco("com apagamentos", "por tamanho");

        assertThat(porNivel.lapides()).isPositive();
        assertThat(porTamanho.lapides()).isGreaterThan(4 * porNivel.lapides());

        // e nenhuma lapide esta entre as chaves vivas: elas sao peso puro
        assertThat(porNivel.entradasNoDisco()).isGreaterThan(porNivel.chavesVivas());
    }

    // ───────────────────── a troca ─────────────────────

    /**
     * A TABELA INTEIRA DO REPOSITORIO EM TRES NUMEROS: cada politica e melhor em duas
     * colunas e pior na terceira.
     */
    @Test
    @DisplayName("por nivel escreve quatro vezes mais e ocupa tres vezes menos")
    void aTabelaDaTroca() {
        assertThat(TROCA).hasSize(2 * 2);

        Medidas.LinhaTroca porNivel = acharTroca("por nivel", false);
        Medidas.LinhaTroca porTamanho = acharTroca("por tamanho", false);

        assertThat(porNivel.escrita()).isGreaterThan(4 * porTamanho.escrita());
        assertThat(porNivel.espaco()).isLessThan(porTamanho.espaco() / 3);
        assertThat(porNivel.leituraDeAusentes()).isLessThan(porTamanho.leituraDeAusentes());

        // e a ultima coluna explica a do meio
        assertThat(porNivel.cruzamentos()).isZero();
        assertThat(porTamanho.cruzamentos()).isPositive();
    }

    /**
     * O ACHADO QUE FECHA O REPOSITORIO: o filtro de Bloom APAGA a diferenca de leitura
     * entre as duas politicas.
     *
     * <p>Sem filtro, a por tamanho busca 7,74 vezes por leitura e a por nivel busca 4,72:
     * uma diferenca de quase duas vezes. Com filtro, as duas ficam em 0,05 e a escolha
     * passa a ser so entre escrita e espaco.
     */
    @Test
    @DisplayName("com filtro, a diferenca de leitura entre as duas politicas quase some")
    void oFiltroApagaADiferencaDeLeitura() {
        Medidas.LinhaTroca semPorNivel = acharTroca("por nivel", false);
        Medidas.LinhaTroca semPorTamanho = acharTroca("por tamanho", false);
        Medidas.LinhaTroca comPorNivel = acharTroca("por nivel", true);
        Medidas.LinhaTroca comPorTamanho = acharTroca("por tamanho", true);

        // sem filtro a diferenca e de quase duas vezes
        assertThat(semPorTamanho.leituraDeAusentes() / semPorNivel.leituraDeAusentes())
                .isGreaterThan(1.5);

        // com filtro as duas sao praticamente iguais e praticamente zero
        assertThat(comPorNivel.leituraDeAusentes()).isLessThan(0.1);
        assertThat(comPorTamanho.leituraDeAusentes()).isLessThan(0.1);
        assertThat(Math.abs(comPorTamanho.leituraDeAusentes() - comPorNivel.leituraDeAusentes()))
                .isLessThan(0.05);

        // e as outras duas amplificacoes nao mudam: o filtro nao mexe em escrita nem espaco
        assertThat(comPorNivel.escrita()).isCloseTo(semPorNivel.escrita(), within(0.01));
        assertThat(comPorNivel.espaco()).isCloseTo(semPorNivel.espaco(), within(0.01));
    }

    // ───────────────────── achadores ─────────────────────

    private static Medidas.LinhaCorrecao acharCorrecao(String roteiro, String politica, boolean comFiltro) {
        return CORRECAO.stream()
                .filter(l -> l.roteiro().equals(roteiro) && l.politica().equals(politica)
                        && l.comFiltro() == comFiltro)
                .findFirst()
                .orElseThrow();
    }

    private static Medidas.LinhaEscrita acharEscrita(String roteiro, String politica) {
        return ESCRITA.stream()
                .filter(l -> l.roteiro().equals(roteiro) && l.politica().equals(politica))
                .findFirst()
                .orElseThrow();
    }

    private static Medidas.LinhaLeitura acharLeitura(String politica, boolean comFiltro, String oQueLe) {
        return LEITURA.stream()
                .filter(l -> l.politica().equals(politica) && l.comFiltro() == comFiltro
                        && l.oQueLe().equals(oQueLe))
                .findFirst()
                .orElseThrow();
    }

    private static Medidas.LinhaEspaco acharEspaco(String roteiro, String politica) {
        return ESPACO.stream()
                .filter(l -> l.roteiro().equals(roteiro) && l.politica().equals(politica))
                .findFirst()
                .orElseThrow();
    }

    private static Medidas.LinhaTroca acharTroca(String politica, boolean comFiltro) {
        return TROCA.stream()
                .filter(l -> l.politica().equals(politica) && l.comFiltro() == comFiltro)
                .findFirst()
                .orElseThrow();
    }
}
