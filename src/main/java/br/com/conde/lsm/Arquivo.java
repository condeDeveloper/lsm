package br.com.conde.lsm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * UM ARQUIVO ORDENADO, que e a peca de que uma arvore de mesclagem e feita.
 *
 * <p>Ele e imutavel. Depois de escrito, nao muda mais: uma chave que for atualizada vai
 * parar num arquivo NOVO, e o antigo fica la com o valor velho ate alguem compactar.
 *
 * <p>Essa imutabilidade e a ideia inteira. Escrever so no fim e barato num disco de
 * verdade e num disco de estado solido, e e o que permite que a escrita seja sequencial.
 * O preco e que o mesmo dado passa a existir em varios lugares, e as tres amplificacoes
 * saem todas disso.
 *
 * <p>Guarda tambem o menor e o maior valor, para uma busca poder descartar o arquivo
 * inteiro sem olhar dentro, e um FILTRO DE BLOOM, para descartar mesmo quando a chave cai
 * na faixa.
 */
public final class Arquivo {

    /** O valor de uma chave, ou a marca de que ela foi apagada. */
    public record Valor(String conteudo, boolean apagado) {

        public static Valor de(String conteudo) {
            return new Valor(conteudo, false);
        }

        /**
         * A LAPIDE: a marca de que a chave foi apagada.
         *
         * <p>Apagar numa arvore de mesclagem e ESCREVER. Nao da para ir ao arquivo antigo
         * e tirar a chave de la, porque ele e imutavel, entao se escreve uma marca nova
         * por cima. A chave apagada passa a ocupar MAIS espaco do que ocupava.
         */
        public static Valor lapide() {
            return new Valor("", true);
        }

        /** Quantos bytes este valor ocupa no arquivo. */
        public int bytes() {
            return apagado ? 1 : conteudo.length();
        }
    }

    private final List<String> chaves;
    private final List<Valor> valores;
    private final Bloom filtro;
    private final long bytes;

    private Arquivo(List<String> chaves, List<Valor> valores, Bloom filtro, long bytes) {
        this.chaves = chaves;
        this.valores = valores;
        this.filtro = filtro;
        this.bytes = bytes;
    }

    /** Escreve um arquivo novo a partir de um mapa ordenado. */
    public static Arquivo escrever(TreeMap<String, Valor> conteudo, boolean comFiltro, Contador contador) {
        List<String> chaves = new ArrayList<>(conteudo.size());
        List<Valor> valores = new ArrayList<>(conteudo.size());
        long bytes = 0;

        for (Map.Entry<String, Valor> entrada : conteudo.entrySet()) {
            chaves.add(entrada.getKey());
            valores.add(entrada.getValue());

            bytes += entrada.getKey().length() + entrada.getValue().bytes();
        }

        contador.escreveu(bytes);

        return new Arquivo(List.copyOf(chaves), List.copyOf(valores),
                comFiltro ? Bloom.de(chaves) : null, bytes);
    }

    public int quantasChaves() {
        return chaves.size();
    }

    public long bytes() {
        return bytes;
    }

    public String menorChave() {
        return chaves.isEmpty() ? null : chaves.get(0);
    }

    public String maiorChave() {
        return chaves.isEmpty() ? null : chaves.get(chaves.size() - 1);
    }

    public boolean temFiltro() {
        return filtro != null;
    }

    public List<String> chaves() {
        return chaves;
    }

    public Valor valorNaPosicao(int i) {
        return valores.get(i);
    }

    /**
     * Procura uma chave neste arquivo, e devolve nulo se ela nao estiver aqui.
     *
     * <p>A ordem das tres conferencias importa e e do mais barato para o mais caro: a
     * faixa de chaves, depois o filtro de Bloom, depois a busca binaria de verdade.
     */
    public Valor procurar(String chave, Contador contador) {
        contador.olhouArquivo();

        if (chaves.isEmpty()) return null;

        if (chave.compareTo(menorChave()) < 0 || chave.compareTo(maiorChave()) > 0) {
            contador.descartouPelaFaixa();

            return null;
        }

        if (filtro != null) {
            contador.consultouFiltro();

            if (!filtro.talvezTenha(chave)) {
                contador.descartouPeloFiltro();

                return null;
            }
        }

        contador.buscouDentro();

        int baixo = 0;
        int alto = chaves.size() - 1;

        while (baixo <= alto) {
            int meio = (baixo + alto) >>> 1;
            int comparacao = chaves.get(meio).compareTo(chave);

            contador.comparou();

            if (comparacao == 0) return valores.get(meio);

            if (comparacao < 0) baixo = meio + 1;
            else alto = meio - 1;
        }

        contador.buscaEmVao();

        return null;
    }

    /** As faixas de dois arquivos se tocam? Uma compactacao por nivel depende disto. */
    public boolean seCruzaCom(Arquivo outro) {
        if (chaves.isEmpty() || outro.chaves.isEmpty()) return false;

        return menorChave().compareTo(outro.maiorChave()) <= 0
                && outro.menorChave().compareTo(maiorChave()) <= 0;
    }

    @Override
    public String toString() {
        return "arquivo de " + chaves.size() + " chaves, de " + menorChave() + " a " + maiorChave();
    }
}
