package br.com.conde.lsm;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.conde.lsm.Juiz.Operacao;
import br.com.conde.lsm.Loja.Politica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Os testes da LOJA contra o ESPELHO.
 *
 * <p>A resposta certa esta num mapa ordenado comum e nao ha duvida possivel sobre ela. O
 * que e dificil nao e saber a resposta: e nao escrever uma arvore de mesclagem que acerta
 * as leituras do teste e erra as outras.
 */
class LojaTest {

    private static final int MEMORIA = 50;
    private static final int POR_NIVEL = 3;

    /**
     * A VARREDURA COMPLETA: todas as formas de roteiro, as duas politicas, com e sem
     * filtro, conferindo TODAS as chaves que ja existiram.
     *
     * <p>Foram duas passagens desta varredura que acharam os dois erros do repositorio.
     * Uma amostra de leituras do que acabou de ser escrito teria passado nas duas.
     */
    @Test
    @DisplayName("toda leitura bate com o espelho, em toda forma de roteiro e nas duas politicas")
    void aVarreduraCompleta() {
        for (long semente = 1; semente <= 4; semente++)
            for (var entrada : Roteiros.todas(3_000, semente).entrySet())
                for (Politica politica : Politica.values())
                    for (boolean comFiltro : new boolean[] {false, true}) {
                        Loja loja = new Loja(MEMORIA, POR_NIVEL, politica, comFiltro, new Contador());

                        Juiz.Veredito veredito = Juiz.julgar(loja, entrada.getValue(), true);

                        assertThat(veredito.divergencias())
                                .as("%s, %s, filtro %s, semente %d: %s", entrada.getKey(),
                                        Medidas.nomeDe(politica), comFiltro, semente,
                                        veredito.primeiraDivergencia())
                                .isZero();

                        assertThat(veredito.leituras()).isPositive();
                    }
    }

    /**
     * O ERRO QUE A COMPACTACAO POR TAMANHO COMETIA: a lapide sumia e o valor VELHO
     * reaparecia.
     *
     * <p>Nessa politica os arquivos de um mesmo nivel tem faixas que se cruzam, entao um
     * arquivo que ja estava no nivel de baixo pode muito bem ter a chave viva. Jogar a
     * lapide fora deixava a chave apagada voltar dos mortos.
     */
    @Test
    @DisplayName("uma chave apagada nao volta dos mortos, nas duas politicas")
    void aChaveApagadaNaoVolta() {
        for (Politica politica : Politica.values()) {
            Loja loja = new Loja(MEMORIA, POR_NIVEL, politica, true, new Contador());
            List<String> apagadas = new ArrayList<>();

            // escreve mil chaves, apaga metade, escreve mais mil para forcar compactacao
            for (int i = 0; i < 1_000; i++) loja.poe(Roteiros.chave(2 * i), Roteiros.valor(i));

            for (int i = 0; i < 1_000; i += 2) {
                loja.apaga(Roteiros.chave(2 * i));
                apagadas.add(Roteiros.chave(2 * i));
            }

            for (int i = 1_000; i < 2_000; i++) loja.poe(Roteiros.chave(2 * i), Roteiros.valor(i));

            loja.descarregar();

            for (String chave : apagadas)
                assertThat(loja.pega(chave)).as("%s em %s", chave, Medidas.nomeDe(politica)).isNull();
        }
    }

    /**
     * O ERRO QUE A MESCLAGEM COMETIA: o valor MAIS ANTIGO ganhava.
     *
     * <p>As duas politicas entregavam a lista de arquivos em ordens de idade opostas, e o
     * mesmo laco de mesclagem fazia o certo numa e o errado na outra.
     */
    @Test
    @DisplayName("depois de muitas reescritas, o valor que vale e o ultimo, nas duas politicas")
    void oValorQueValeEOUltimo() {
        for (Politica politica : Politica.values()) {
            Loja loja = new Loja(MEMORIA, POR_NIVEL, politica, true, new Contador());
            TreeMap<String, String> espelho = new TreeMap<>();
            Roteiros.Sorteio sorteio = new Roteiros.Sorteio(9);

            for (int i = 0; i < 20_000; i++) {
                String chave = Roteiros.chave(2 * sorteio.ate(200));
                String valor = Roteiros.valor(i);

                loja.poe(chave, valor);
                espelho.put(chave, valor);
            }

            loja.descarregar();

            espelho.forEach((chave, valor) ->
                    assertThat(loja.pega(chave)).as("%s em %s", chave, Medidas.nomeDe(politica))
                            .isEqualTo(valor));
        }
    }

    @Test
    @DisplayName("uma chave que nunca existiu devolve nulo, e uma apagada tambem")
    void oQueNaoExisteEoApagadoDaoNulo() {
        Loja loja = new Loja(MEMORIA, POR_NIVEL, Politica.POR_NIVEL, true, new Contador());

        loja.poe("a", "um");
        loja.apaga("a");

        assertThat(loja.pega("a")).isNull();
        assertThat(loja.pega("nunca existiu")).isNull();
    }

    @Test
    @DisplayName("uma loja vazia responde nulo e nao tem nivel nenhum")
    void aLojaVazia() {
        Loja loja = new Loja(MEMORIA, POR_NIVEL, Politica.POR_NIVEL, true, new Contador());

        assertThat(loja.pega("qualquer")).isNull();
        assertThat(loja.quantosNiveis()).isZero();
        assertThat(loja.quantosArquivos()).isZero();
        assertThat(loja.bytesNoDisco()).isZero();

        // descarregar uma memoria vazia nao cria arquivo nenhum
        loja.descarregar();

        assertThat(loja.quantosArquivos()).isZero();
    }

    @Test
    @DisplayName("a loja recusa memoria ou nivel que nao cabem nada")
    void osParametrosInvalidos() {
        assertThatThrownBy(() -> new Loja(0, POR_NIVEL, Politica.POR_NIVEL, true, new Contador()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("memoria");

        assertThatThrownBy(() -> new Loja(MEMORIA, 1, Politica.POR_NIVEL, true, new Contador()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nivel");
    }

    /**
     * A PROPRIEDADE QUE DEFINE A COMPACTACAO POR NIVEL: os arquivos de um mesmo nivel nao
     * tem faixas que se cruzam.
     *
     * <p>E dela que sai a amplificacao de leitura baixa: se as faixas nao se cruzam, uma
     * leitura olha no maximo um arquivo por nivel.
     */
    @Test
    @DisplayName("por nivel, nenhum par de arquivos do mesmo nivel tem faixas que se cruzam")
    void porNivelAsFaixasNaoSeCruzam() {
        Loja loja = new Loja(MEMORIA, POR_NIVEL, Politica.POR_NIVEL, true, new Contador());

        for (Operacao operacao : Roteiros.repetidas(20_000, 2_000, 1))
            loja.poe(operacao.chave(), operacao.valor());

        loja.descarregar();

        assertThat(loja.cruzamentosNoMesmoNivel()).isZero();
        assertThat(loja.quantosNiveis()).isGreaterThan(1);
    }

    @Test
    @DisplayName("por tamanho, os arquivos de um mesmo nivel se cruzam, e e disso que vem a leitura cara")
    void porTamanhoAsFaixasSeCruzam() {
        Loja loja = new Loja(MEMORIA, POR_NIVEL, Politica.POR_TAMANHO, true, new Contador());

        for (Operacao operacao : Roteiros.repetidas(20_000, 2_000, 1))
            loja.poe(operacao.chave(), operacao.valor());

        loja.descarregar();

        assertThat(loja.cruzamentosNoMesmoNivel()).isPositive();
    }

    @Test
    @DisplayName("os niveis crescem em dez por nivel, e ficam iguais por tamanho")
    void oTamanhoDeCadaNivel() {
        Loja porNivel = new Loja(MEMORIA, 4, Politica.POR_NIVEL, true, new Contador());
        Loja porTamanho = new Loja(MEMORIA, 4, Politica.POR_TAMANHO, true, new Contador());

        assertThat(porNivel.cabemNoNivel(0)).isEqualTo(4);
        assertThat(porNivel.cabemNoNivel(1)).isEqualTo(40);
        assertThat(porNivel.cabemNoNivel(2)).isEqualTo(400);

        assertThat(porTamanho.cabemNoNivel(0)).isEqualTo(4);
        assertThat(porTamanho.cabemNoNivel(3)).isEqualTo(4);
    }

    /**
     * APAGAR E ESCREVER: a chave apagada ocupa MAIS lugar do que ocupava, e continua
     * ocupando ate a compactacao chegar ao ultimo nivel.
     */
    @Test
    @DisplayName("apagar aumenta os bytes no disco em vez de diminuir")
    void apagarAumentaODisco() {
        Loja loja = new Loja(MEMORIA, POR_NIVEL, Politica.POR_NIVEL, true, new Contador());

        for (int i = 0; i < 100; i++) loja.poe(Roteiros.chave(2 * i), Roteiros.valor(i));

        loja.descarregar();

        long antes = loja.bytesNoDisco();

        for (int i = 0; i < 100; i++) loja.apaga(Roteiros.chave(2 * i));

        loja.descarregar();

        assertThat(loja.bytesNoDisco()).as("depois de apagar tudo").isGreaterThan(antes);
        assertThat(loja.lapides()).isEqualTo(100);

        // e todas as chaves continuam respondendo nulo
        for (int i = 0; i < 100; i++) assertThat(loja.pega(Roteiros.chave(2 * i))).isNull();
    }

    @Test
    @DisplayName("o contador conta os bytes do usuario e os do disco separados")
    void oContadorSepara() {
        Contador contador = new Contador();
        Loja loja = new Loja(MEMORIA, POR_NIVEL, Politica.POR_NIVEL, true, contador);

        for (int i = 0; i < 1_000; i++) loja.poe(Roteiros.chave(2 * i), Roteiros.valor(i));

        loja.descarregar();

        assertThat(contador.bytesDoUsuario()).isPositive();
        assertThat(contador.bytesEscritos()).isGreaterThanOrEqualTo(contador.bytesDoUsuario());
        assertThat(contador.amplificacaoDeEscrita()).isGreaterThanOrEqualTo(1.0);

        contador.zerar();

        assertThat(contador.amplificacaoDeEscrita()).isZero();
        assertThat(contador.arquivosPorLeitura()).isZero();
    }
}
