package br.com.conde.lsm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.conde.lsm.Juiz.Operacao;

/**
 * OS ROTEIROS DE OPERACOES que as tabelas medem.
 *
 * <p>A forma do roteiro decide tudo. Chaves SEMPRE NOVAS nao geram valor velho nenhum e a
 * amplificacao de espaco fica em um. Chaves REPETIDAS geram, e e ai que compactar passa a
 * valer a pena. Chaves em ORDEM CRESCENTE nunca fazem faixas se cruzarem, e a compactacao
 * por tamanho parece tao boa quanto a por nivel.
 *
 * <p>Todos sao gerados por semente fixa. A integracao continua roda em tres sistemas, e
 * "a amplificacao de escrita foi 7,4" com roteiros diferentes em cada sistema nao e uma
 * medida.
 */
public final class Roteiros {

    private Roteiros() {
    }

    /** Quantos caracteres tem o valor de cada chave nos roteiros. */
    public static final int TAMANHO_DO_VALOR = 40;

    /**
     * O CONGRUENTE LINEAR do repositorio, com o descarte dos bits de baixo.
     *
     * <p>O deslocamento de 32 bits tira os bits que alternam par e impar a cada passo. Sem
     * ele, as chaves sorteadas alternariam par e impar, e um roteiro em xadrez e um
     * roteiro ORDENADO disfarcado: as faixas sairiam previsiveis e a compactacao por
     * tamanho pareceria melhor do que e.
     */
    public static final class Sorteio {

        private long estado;

        public Sorteio(long semente) {
            this.estado = semente == 0 ? 1 : semente;
        }

        public int proximo() {
            estado = estado * 6364136223846793005L + 1442695040888963407L;

            return (int) (estado >>> 32);
        }

        public int ate(int limite) {
            if (limite <= 0) throw new IllegalArgumentException("o limite tem de ser positivo");

            return (int) (Integer.toUnsignedLong(proximo()) % limite);
        }

        public double fracao() {
            return Integer.toUnsignedLong(proximo()) / 4294967296.0;
        }
    }

    /**
     * Uma chave de tamanho fixo, para o espaco nao depender do numero.
     *
     * <p>Todo roteiro de ESCRITA usa numeros PARES e o de leitura de ausentes usa IMPARES.
     *
     * <p>Isso nao e enfeite. A primeira versao punha as chaves ausentes acima de todas as
     * escritas, e com isso TODO arquivo era descartado pela faixa de chaves antes de
     * qualquer busca. A tabela da leitura de ausentes dava zero busca por leitura nas
     * quatro linhas, e o filtro de Bloom aparecia sem servir para nada.
     *
     * <p>Chave ausente de verdade cai no MEIO das que existem.
     */
    public static String chave(int numero) {
        return String.format("chave%08d", numero);
    }

    /** O numero da n-esima chave escrita: sempre par. */
    static int numeroDeEscrita(int n) {
        return 2 * n;
    }

    /** O numero da n-esima chave ausente: sempre impar, entre duas escritas. */
    static int numeroAusente(int n) {
        return 2 * n + 1;
    }

    /** Um valor de tamanho fixo, pelo mesmo motivo. */
    public static String valor(int numero) {
        String comeco = "v" + numero;

        return comeco + "x".repeat(Math.max(0, TAMANHO_DO_VALOR - comeco.length()));
    }

    /** Chaves SEMPRE NOVAS. Nenhum valor fica velho e nada ha para compactar. */
    public static List<Operacao> sempreNovas(int quantas, long semente) {
        Sorteio sorteio = new Sorteio(semente);
        List<Operacao> roteiro = new ArrayList<>();
        List<Integer> ordem = new ArrayList<>();

        for (int i = 0; i < quantas; i++) ordem.add(i);

        // embaralha para as chaves nao sairem em ordem crescente, que e outro roteiro
        for (int i = quantas - 1; i > 0; i--) {
            int j = sorteio.ate(i + 1);
            int guarda = ordem.get(i);

            ordem.set(i, ordem.get(j));
            ordem.set(j, guarda);
        }

        for (int numero : ordem) roteiro.add(Operacao.poe(chave(numeroDeEscrita(numero)), valor(numero)));

        return roteiro;
    }

    /** Chaves sorteadas de um conjunto PEQUENO: cada uma e reescrita muitas vezes. */
    public static List<Operacao> repetidas(int quantas, int quantasChaves, long semente) {
        Sorteio sorteio = new Sorteio(semente);
        List<Operacao> roteiro = new ArrayList<>();

        for (int i = 0; i < quantas; i++)
            roteiro.add(Operacao.poe(chave(numeroDeEscrita(sorteio.ate(quantasChaves))), valor(i)));

        return roteiro;
    }

    /** Chaves em ORDEM CRESCENTE, que e o que um carimbo de tempo produz. */
    public static List<Operacao> emOrdem(int quantas) {
        List<Operacao> roteiro = new ArrayList<>();

        for (int i = 0; i < quantas; i++) roteiro.add(Operacao.poe(chave(numeroDeEscrita(i)), valor(i)));

        return roteiro;
    }

    /** Escreve, apaga uma fracao e escreve de novo. As lapides vivem aqui. */
    public static List<Operacao> comApagamentos(int quantas, int quantasChaves,
                                                double fracaoApagada, long semente) {

        Sorteio sorteio = new Sorteio(semente);
        List<Operacao> roteiro = new ArrayList<>();

        for (int i = 0; i < quantas; i++) {
            int numero = sorteio.ate(quantasChaves);

            if (sorteio.fracao() < fracaoApagada) roteiro.add(Operacao.apaga(chave(numeroDeEscrita(numero))));
            else roteiro.add(Operacao.poe(chave(numeroDeEscrita(numero)), valor(i)));
        }

        return roteiro;
    }

    /** Leituras de chaves que EXISTEM. */
    public static List<Operacao> leituraDeExistentes(int quantas, int quantasChaves, long semente) {
        Sorteio sorteio = new Sorteio(semente);
        List<Operacao> roteiro = new ArrayList<>();

        for (int i = 0; i < quantas; i++)
            roteiro.add(Operacao.le(chave(numeroDeEscrita(sorteio.ate(quantasChaves)))));

        return roteiro;
    }

    /**
     * Leituras de chaves que NAO existem, que e o caso que custa caro.
     *
     * <p>Achar uma chave que existe custa olhar os arquivos ate ela aparecer. Descobrir
     * que uma chave nao existe custa olhar TODOS, ate o ultimo, para ter certeza.
     */
    public static List<Operacao> leituraDeAusentes(int quantas, int quantasChaves, long semente) {
        Sorteio sorteio = new Sorteio(semente);
        List<Operacao> roteiro = new ArrayList<>();

        for (int i = 0; i < quantas; i++)
            roteiro.add(Operacao.le(chave(numeroAusente(sorteio.ate(quantasChaves)))));

        return roteiro;
    }

    /** Junta dois roteiros num so. */
    public static List<Operacao> juntar(List<Operacao> um, List<Operacao> outro) {
        List<Operacao> junto = new ArrayList<>(um);

        junto.addAll(outro);

        return junto;
    }

    /** As formas de escrita, na ordem em que as tabelas as mostram. */
    public static Map<String, List<Operacao>> todas(int quantas, long semente) {
        Map<String, List<Operacao>> mapa = new LinkedHashMap<>();

        mapa.put("sempre novas", sempreNovas(quantas, semente));
        mapa.put("em ordem", emOrdem(quantas));
        mapa.put("repetidas em 1000", repetidas(quantas, 1_000, semente));
        mapa.put("repetidas em 100", repetidas(quantas, 100, semente));
        mapa.put("com apagamentos", comApagamentos(quantas, 1_000, 0.3, semente));

        return mapa;
    }
}
