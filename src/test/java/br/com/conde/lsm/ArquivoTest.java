package br.com.conde.lsm;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.conde.lsm.Arquivo.Valor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Os testes do ARQUIVO ORDENADO e do FILTRO DE BLOOM. */
class ArquivoTest {

    private final Contador contador = new Contador();

    @Test
    @DisplayName("o arquivo acha o que esta nele e nao acha o que nao esta")
    void oArquivoAchaOQueTem() {
        Arquivo arquivo = comAsChaves(0, 100);

        for (int i = 0; i < 100; i++) {
            Valor achado = arquivo.procurar(Roteiros.chave(2 * i), contador);

            assertThat(achado).as("chave %d", 2 * i).isNotNull();
            assertThat(achado.conteudo()).isEqualTo(Roteiros.valor(i));
        }

        // uma chave impar cai no meio das pares e nao esta la
        assertThat(arquivo.procurar(Roteiros.chave(51), contador)).isNull();
    }

    /**
     * A ORDEM DAS TRES CONFERENCIAS importa e e do mais barato para o mais caro: a faixa
     * de chaves, depois o filtro, depois a busca binaria.
     */
    @Test
    @DisplayName("uma chave fora da faixa e descartada sem consultar o filtro nem buscar")
    void aFaixaDescartaAntesDoFiltro() {
        Contador proprio = new Contador();
        Arquivo arquivo = comAsChaves(100, 200);

        arquivo.procurar(Roteiros.chave(0), proprio);

        assertThat(proprio.descartesPelaFaixa()).isEqualTo(1);
        assertThat(proprio.consultasAoFiltro()).isZero();
        assertThat(proprio.buscasDentro()).isZero();
    }

    /**
     * O FILTRO DE BLOOM corta a busca em vao, que e o trabalho desperdicado que ele existe
     * para evitar.
     */
    @Test
    @DisplayName("com filtro, uma chave ausente dentro da faixa quase nunca chega a busca binaria")
    void oFiltroCortaABuscaEmVao() {
        Contador comFiltro = new Contador();
        Contador semFiltro = new Contador();

        Arquivo arquivoComFiltro = comAsChaves(0, 1_000, true);
        Arquivo arquivoSemFiltro = comAsChaves(0, 1_000, false);

        for (int i = 0; i < 1_000; i++) {
            arquivoComFiltro.procurar(Roteiros.chave(2 * i + 1), comFiltro);
            arquivoSemFiltro.procurar(Roteiros.chave(2 * i + 1), semFiltro);
        }

        // 999 e nao 1000: a ultima chave impar, a 1999, fica ACIMA da maior do arquivo,
        // que e a 1998, e e descartada pela faixa antes de qualquer busca
        assertThat(semFiltro.buscasEmVao()).isEqualTo(999);
        assertThat(semFiltro.descartesPelaFaixa()).isEqualTo(1);
        assertThat(comFiltro.buscasEmVao()).isLessThan(50);
        assertThat(comFiltro.descartesPeloFiltro()).isGreaterThan(950);
    }

    @Test
    @DisplayName("as faixas de dois arquivos se tocam ou nao, e o teste e simetrico")
    void oCruzamentoDeFaixas() {
        Arquivo um = comAsChaves(0, 100);
        Arquivo vizinho = comAsChaves(100, 200);
        Arquivo sobreposto = comAsChaves(50, 150);

        assertThat(um.seCruzaCom(vizinho)).isFalse();
        assertThat(vizinho.seCruzaCom(um)).isFalse();
        assertThat(um.seCruzaCom(sobreposto)).isTrue();
        assertThat(sobreposto.seCruzaCom(um)).isTrue();
    }

    @Test
    @DisplayName("um arquivo vazio nao se cruza com nada e nao tem menor nem maior chave")
    void oArquivoVazio() {
        Arquivo vazio = Arquivo.escrever(new TreeMap<>(), true, contador);

        assertThat(vazio.quantasChaves()).isZero();
        assertThat(vazio.menorChave()).isNull();
        assertThat(vazio.maiorChave()).isNull();
        assertThat(vazio.bytes()).isZero();
        assertThat(vazio.seCruzaCom(comAsChaves(0, 10))).isFalse();
        assertThat(vazio.procurar("qualquer", contador)).isNull();
    }

    @Test
    @DisplayName("uma lapide ocupa um byte e um valor ocupa o tamanho dele")
    void oTamanhoDoValor() {
        assertThat(Valor.lapide().bytes()).isEqualTo(1);
        assertThat(Valor.lapide().apagado()).isTrue();
        assertThat(Valor.de("abcde").bytes()).isEqualTo(5);
        assertThat(Valor.de("abcde").apagado()).isFalse();
    }

    @Test
    @DisplayName("o arquivo diz quantos bytes ocupa e o contador registra a escrita")
    void osBytesDoArquivo() {
        Contador proprio = new Contador();
        TreeMap<String, Valor> conteudo = new TreeMap<>();

        conteudo.put("aa", Valor.de("12345"));
        conteudo.put("bb", Valor.lapide());

        Arquivo arquivo = Arquivo.escrever(conteudo, false, proprio);

        assertThat(arquivo.bytes()).isEqualTo(2 + 5 + 2 + 1);
        assertThat(proprio.bytesEscritos()).isEqualTo(arquivo.bytes());
        assertThat(arquivo.temFiltro()).isFalse();
    }

    // ───────────────────────────── o filtro ─────────────────────────────

    /**
     * O FILTRO ERRA NUM SENTIDO SO. Dizer "talvez sim" sobre uma chave que nao esta ali
     * custa uma busca em vao; dizer "nao" sobre uma chave que esta ali seria perder um
     * dado, e isso ele nao faz nunca.
     */
    @Test
    @DisplayName("o filtro nunca diz nao sobre uma chave que esta la")
    void oFiltroNuncaPerdeUmaChave() {
        List<String> chaves = new ArrayList<>();

        for (int i = 0; i < 5_000; i++) chaves.add(Roteiros.chave(2 * i));

        Bloom filtro = Bloom.de(chaves);

        for (String chave : chaves) assertThat(filtro.talvezTenha(chave)).as(chave).isTrue();
    }

    @Test
    @DisplayName("o falso positivo do filtro fica perto de um por cento")
    void oFalsoPositivoDoFiltro() {
        List<String> chaves = new ArrayList<>();

        for (int i = 0; i < 10_000; i++) chaves.add(Roteiros.chave(2 * i));

        Bloom filtro = Bloom.de(chaves);
        int falsos = 0;

        for (int i = 0; i < 10_000; i++) if (filtro.talvezTenha(Roteiros.chave(2 * i + 1))) falsos++;

        assertThat((double) falsos / 10_000).isCloseTo(0.01, within(0.015));
    }

    @Test
    @DisplayName("o filtro ocupa dez bits por chave e nunca menos de sessenta e quatro")
    void oTamanhoDoFiltro() {
        assertThat(Bloom.de(List.of("a")).tamanho()).isEqualTo(64);
        assertThat(Bloom.de(List.of("a")).bytes()).isEqualTo(8);

        List<String> mil = new ArrayList<>();

        for (int i = 0; i < 1_000; i++) mil.add(Roteiros.chave(i));

        assertThat(Bloom.de(mil).tamanho()).isEqualTo(10_000);
    }

    /**
     * A MISTURA existe porque o {@code hashCode} de uma cadeia espalha mal nos bits de
     * cima, e o filtro usa os de cima e os de baixo como duas funcoes diferentes.
     */
    @Test
    @DisplayName("a mistura espalha os bits de cima, que o hashCode da cadeia nao espalha")
    void aMisturaEspalha() {
        long somaDoCru = 0;
        long somaDaMistura = 0;
        int quantas = 10_000;

        for (int i = 0; i < quantas; i++) {
            String uma = Roteiros.chave(2 * i);
            String outra = Roteiros.chave(2 * i + 2);

            somaDoCru += Long.bitCount((long) uma.hashCode() ^ (long) outra.hashCode());
            somaDaMistura += Long.bitCount(Bloom.mistura(uma) ^ Bloom.mistura(outra));
        }

        assertThat((double) somaDaMistura / quantas).as("a mistura muda perto de 32 bits")
                .isBetween(30.0, 34.0);
        assertThat((double) somaDoCru / quantas).as("o hashCode cru muda muito menos")
                .isLessThan(10.0);
    }

    private Arquivo comAsChaves(int de, int ate) {
        return comAsChaves(de, ate, true);
    }

    private Arquivo comAsChaves(int de, int ate, boolean comFiltro) {
        TreeMap<String, Valor> conteudo = new TreeMap<>();

        for (int i = de; i < ate; i++) conteudo.put(Roteiros.chave(2 * i), Valor.de(Roteiros.valor(i)));

        return Arquivo.escrever(conteudo, comFiltro, contador);
    }
}
