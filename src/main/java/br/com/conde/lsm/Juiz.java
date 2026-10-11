package br.com.conde.lsm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * O JUIZ: um MAPA ORDENADO comum, com o mesmo conteudo.
 *
 * <p>Este e o repositorio mais facil de julgar da serie e o mais facil de enganar sem
 * perceber. A resposta certa esta num {@code TreeMap} e nao ha duvida possivel sobre ela.
 * O que e dificil nao e saber a resposta: e nao escrever uma arvore de mesclagem que
 * acerta as leituras do teste e erra as outras.
 *
 * <p>Por isso o juiz nao confere amostras. Ao fim do roteiro ele pode conferir TODAS as
 * chaves que ja existiram, inclusive as apagadas, que sao justamente as que a arvore erra
 * quando a ordem de mesclagem esta trocada.
 */
public final class Juiz {

    private Juiz() {
    }

    /** O que aconteceu ao rodar um roteiro inteiro. */
    public record Veredito(int operacoes, int leituras, int divergencias,
                           String primeiraDivergencia, long bytesNoDisco,
                           long bytesVivos, long lapides, int arquivos, int niveis) {

        public boolean bateu() {
            return divergencias == 0;
        }

        /** Quantos bytes o disco ocupa por byte que ainda vale. */
        public double amplificacaoDeEspaco() {
            return bytesVivos == 0 ? 0 : (double) bytesNoDisco / bytesVivos;
        }
    }

    /** Uma operacao do roteiro. */
    public record Operacao(Tipo tipo, String chave, String valor) {

        public enum Tipo {
            POE, APAGA, LE
        }

        public static Operacao poe(String chave, String valor) {
            return new Operacao(Tipo.POE, chave, valor);
        }

        public static Operacao apaga(String chave) {
            return new Operacao(Tipo.APAGA, chave, null);
        }

        public static Operacao le(String chave) {
            return new Operacao(Tipo.LE, chave, null);
        }
    }

    /**
     * Roda o roteiro na loja e no espelho ao mesmo tempo, e compara.
     *
     * @param conferirTudoAoFim se verdadeiro, confere TODAS as chaves que ja existiram no
     *     fim, e nao so as que o roteiro leu
     */
    public static Veredito julgar(Loja loja, List<Operacao> roteiro, boolean conferirTudoAoFim) {
        TreeMap<String, String> espelho = new TreeMap<>();
        List<String> jaExistiram = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        int leituras = 0;
        int divergencias = 0;
        String primeira = null;

        for (Operacao operacao : roteiro) {
            if (operacao.tipo() != Operacao.Tipo.LE && vistas.add(operacao.chave()))
                jaExistiram.add(operacao.chave());

            switch (operacao.tipo()) {
                case POE -> {
                    loja.poe(operacao.chave(), operacao.valor());
                    espelho.put(operacao.chave(), operacao.valor());
                }
                case APAGA -> {
                    loja.apaga(operacao.chave());
                    espelho.remove(operacao.chave());
                }
                case LE -> {
                    leituras++;

                    String daLoja = loja.pega(operacao.chave());
                    String doEspelho = espelho.get(operacao.chave());

                    if (!iguais(daLoja, doEspelho)) {
                        divergencias++;

                        if (primeira == null)
                            primeira = operacao.chave() + ": a loja disse " + daLoja
                                    + " e o espelho disse " + doEspelho;
                    }
                }
                default -> throw new IllegalStateException("operacao desconhecida: " + operacao.tipo());
            }
        }

        if (conferirTudoAoFim)
            for (String chave : jaExistiram) {
                leituras++;

                String daLoja = loja.pega(chave);
                String doEspelho = espelho.get(chave);

                if (!iguais(daLoja, doEspelho)) {
                    divergencias++;

                    if (primeira == null)
                        primeira = chave + ": a loja disse " + daLoja + " e o espelho disse " + doEspelho;
                }
            }

        long bytesVivos = 0;

        for (Map.Entry<String, String> entrada : espelho.entrySet())
            bytesVivos += entrada.getKey().length() + entrada.getValue().length();

        return new Veredito(roteiro.size(), leituras, divergencias, primeira,
                loja.bytesNoDisco(), bytesVivos, loja.lapides(),
                loja.quantosArquivos(), loja.quantosNiveis());
    }

    private static boolean iguais(String um, String outro) {
        return um == null ? outro == null : um.equals(outro);
    }

    /** Quantas chaves distintas um roteiro toca. */
    public static int chavesDistintas(List<Operacao> roteiro) {
        Set<String> chaves = new HashSet<>();

        for (Operacao operacao : roteiro) chaves.add(operacao.chave());

        return chaves.size();
    }
}
