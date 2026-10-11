package br.com.conde.lsm;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import br.com.conde.lsm.Arquivo.Valor;

/**
 * A LOJA: uma arvore de mesclagem estruturada em log.
 *
 * <p>Toda escrita vai para a MEMORIA, que e um mapa ordenado pequeno. Quando ela enche,
 * vira um arquivo imutavel no nivel zero. Os arquivos vao sendo compactados de nivel em
 * nivel conforme cada nivel enche.
 *
 * <p>Uma leitura procura da memoria para baixo e para no primeiro lugar em que a chave
 * aparecer, porque o mais recente esta sempre mais em cima. Uma lapide encontrada no
 * caminho quer dizer apagado, e a busca para ali.
 *
 * <p>As duas politicas de compactacao desta classe nao sao melhor e pior: elas trocam uma
 * amplificacao por outra, e a tabela mede a troca.
 */
public final class Loja {

    /** Como compactar quando um nivel enche. */
    public enum Politica {

        /**
         * POR NIVEL: cada nivel guarda arquivos de faixas que NAO se cruzam.
         *
         * <p>Quando o nivel enche, um arquivo dele e mesclado com todos os do nivel de
         * baixo cuja faixa toca a dele, e o resultado substitui os dois lados.
         *
         * <p>Como as faixas nao se cruzam, uma leitura olha NO MAXIMO UM arquivo por
         * nivel. O preco e reescrever o mesmo dado muitas vezes.
         */
        POR_NIVEL,

        /**
         * POR TAMANHO: quando ha arquivos demais num nivel, mescla TODOS de uma vez e
         * joga o resultado no nivel de baixo.
         *
         * <p>Cada dado e reescrito muito menos vezes, e em compensacao o mesmo nivel passa
         * a ter varios arquivos de faixas que se cruzam. Uma leitura pode ter de olhar
         * todos eles.
         */
        POR_TAMANHO
    }

    private final int chavesNaMemoria;
    private final int arquivosPorNivel;
    private final Politica politica;
    private final boolean comFiltro;
    private final Contador contador;

    private final TreeMap<String, Valor> memoria = new TreeMap<>();
    private final List<List<Arquivo>> niveis = new ArrayList<>();

    public Loja(int chavesNaMemoria, int arquivosPorNivel, Politica politica,
                boolean comFiltro, Contador contador) {

        if (chavesNaMemoria <= 0) throw new IllegalArgumentException("a memoria tem de caber ao menos uma chave");
        if (arquivosPorNivel <= 1) throw new IllegalArgumentException("um nivel tem de caber ao menos dois arquivos");

        this.chavesNaMemoria = chavesNaMemoria;
        this.arquivosPorNivel = arquivosPorNivel;
        this.politica = politica;
        this.comFiltro = comFiltro;
        this.contador = contador;
    }

    public void poe(String chave, String valor) {
        contador.usuarioGravou(chave.length() + valor.length());
        escrever(chave, Valor.de(valor));
    }

    /**
     * APAGAR E ESCREVER.
     *
     * <p>Nao da para ir ao arquivo antigo e tirar a chave de la, porque ele e imutavel.
     * Escreve-se uma LAPIDE por cima, e a chave apagada passa a ocupar MAIS espaco do que
     * ocupava.
     */
    public void apaga(String chave) {
        contador.usuarioGravou(chave.length() + 1);
        escrever(chave, Valor.lapide());
    }

    private void escrever(String chave, Valor valor) {
        memoria.put(chave, valor);

        if (memoria.size() >= chavesNaMemoria) descarregar();
    }

    /**
     * Procura a chave da memoria para baixo e para no primeiro lugar em que ela aparecer.
     *
     * <p>Devolve nulo tanto para "nunca existiu" quanto para "foi apagada". Do lado de
     * fora as duas coisas sao a mesma; do lado de dentro, a segunda custa mais.
     */
    public String pega(String chave) {
        contador.leu();

        Valor naMemoria = memoria.get(chave);

        if (naMemoria != null) return naMemoria.apagado() ? null : naMemoria.conteudo();

        for (List<Arquivo> nivel : niveis)
            // de tras para a frente: no nivel zero, o arquivo mais novo esta no fim
            for (int i = nivel.size() - 1; i >= 0; i--) {
                Valor achado = nivel.get(i).procurar(chave, contador);

                if (achado != null) return achado.apagado() ? null : achado.conteudo();
            }

        return null;
    }

    /** Esvazia a memoria para um arquivo novo do nivel zero. */
    public void descarregar() {
        if (memoria.isEmpty()) return;

        poeNoNivel(0, Arquivo.escrever(new TreeMap<>(memoria), comFiltro, contador));
        memoria.clear();

        compactarSePreciso();
    }

    private void poeNoNivel(int nivel, Arquivo arquivo) {
        while (niveis.size() <= nivel) niveis.add(new ArrayList<>());

        niveis.get(nivel).add(arquivo);
    }

    private void compactarSePreciso() {
        for (int nivel = 0; nivel < niveis.size(); nivel++) {
            int cabem = cabemNoNivel(nivel);

            while (niveis.get(nivel).size() > cabem) {
                if (politica == Politica.POR_NIVEL) compactarUmArquivo(nivel);
                else compactarTudo(nivel);

                contador.compactou();
            }
        }
    }

    /**
     * Quantos arquivos cabem num nivel.
     *
     * <p>Por nivel, o zero cabe poucos e os de baixo crescem em dez: e o que mantem as
     * faixas sem se cruzar sem reescrever o mundo a cada vez.
     *
     * <p>Por tamanho, todo nivel cabe o mesmo numero de arquivos, e quando enche vai tudo
     * junto para baixo.
     */
    int cabemNoNivel(int nivel) {
        if (politica == Politica.POR_TAMANHO) return arquivosPorNivel;

        return nivel == 0 ? arquivosPorNivel : arquivosPorNivel * (int) Math.pow(10, nivel);
    }

    /** POR NIVEL: um arquivo deste nivel desce e e mesclado com quem o toca embaixo. */
    private void compactarUmArquivo(int nivel) {
        List<Arquivo> deCima = niveis.get(nivel);
        Arquivo escolhido = deCima.remove(0);

        while (niveis.size() <= nivel + 1) niveis.add(new ArrayList<>());

        List<Arquivo> deBaixo = niveis.get(nivel + 1);
        List<Arquivo> aMesclar = new ArrayList<>();

        // os de baixo sao os ANTIGOS e entram primeiro; o que desceu e o NOVO e entra por
        // ultimo. A ordem desta lista e o que decide qual valor sobrevive na mesclagem
        for (int i = deBaixo.size() - 1; i >= 0; i--)
            if (deBaixo.get(i).seCruzaCom(escolhido)) aMesclar.add(deBaixo.remove(i));

        aMesclar.add(escolhido);

        /*
         * A LAPIDE SO PODE SUMIR quando nao houver mais nenhum arquivo ABAIXO que possa
         * ter a chave viva.
         *
         * Por nivel isso vale no ultimo nivel: os arquivos que sobraram ali tem faixas que
         * NAO tocam a do arquivo que desceu, entao nenhum deles pode ter a chave.
         */
        boolean podeApagarLapides = nivel + 1 >= niveis.size() - 1;

        deBaixo.add(mesclar(aMesclar, podeApagarLapides));
    }

    /** POR TAMANHO: todos os arquivos deste nivel viram um so, no nivel de baixo. */
    private void compactarTudo(int nivel) {
        List<Arquivo> todos = new ArrayList<>(niveis.get(nivel));

        niveis.get(nivel).clear();

        while (niveis.size() <= nivel + 1) niveis.add(new ArrayList<>());

        /*
         * Por TAMANHO nao vale a mesma coisa, e foi aqui que a arvore RESSUSCITOU chaves
         * apagadas.
         *
         * Nesta politica os arquivos de um mesmo nivel tem faixas que se cruzam, entao um
         * arquivo que ja estava no nivel de baixo pode muito bem ter a chave viva. Jogar a
         * lapide fora deixava o valor velho reaparecer, e o espelho pegou 620 divergencias.
         *
         * A lapide so some quando o nivel de destino e o ultimo E esta vazio.
         */
        boolean podeApagarLapides = nivel + 1 >= niveis.size() - 1 && niveis.get(nivel + 1).isEmpty();

        niveis.get(nivel + 1).add(mesclar(todos, podeApagarLapides));
    }

    /**
     * Mescla uma lista de arquivos num so, do mais ANTIGO para o mais NOVO.
     *
     * <p>A ordem importa e foi a parte que deu errado. O valor que vale e o do arquivo
     * mais NOVO, e ele precisa ser o ULTIMO a ser posto no mapa.
     *
     * <p>A primeira versao percorria a lista ao contrario, porque na compactacao por nivel
     * ela chegava com o arquivo mais novo na frente. Na compactacao por tamanho ela chega
     * na ordem oposta, e o mesmo laco fazia o valor MAIS ANTIGO ganhar. O espelho pegou
     * 427 divergencias e nenhuma delas aparecia na politica por nivel.
     *
     * <p>Agora as duas politicas entregam a lista do mais ANTIGO para o mais NOVO, e o
     * laco anda para a frente.
     *
     * <p>As LAPIDES so somem quando quem chama garante que nao ha mais nenhum arquivo
     * abaixo capaz de ter a chave viva. Cada politica tem a sua condicao, e elas nao sao a
     * mesma: foi confundir as duas que fez a arvore ressuscitar chaves apagadas.
     */
    private Arquivo mesclar(List<Arquivo> doMaisAntigoAoMaisNovo, boolean podeApagarLapides) {
        TreeMap<String, Valor> junto = new TreeMap<>();

        for (Arquivo arquivo : doMaisAntigoAoMaisNovo)
            for (int j = 0; j < arquivo.quantasChaves(); j++)
                junto.put(arquivo.chaves().get(j), arquivo.valorNaPosicao(j));

        if (podeApagarLapides) junto.values().removeIf(Valor::apagado);

        return Arquivo.escrever(junto, comFiltro, contador);
    }

    // ───────────────────── o que as tabelas perguntam ─────────────────────

    public int quantosNiveis() {
        return niveis.size();
    }

    public int quantosArquivos() {
        int quantos = 0;

        for (List<Arquivo> nivel : niveis) quantos += nivel.size();

        return quantos;
    }

    public int arquivosNoNivel(int nivel) {
        return nivel < niveis.size() ? niveis.get(nivel).size() : 0;
    }

    /** Quantos bytes o disco ocupa agora, contando valor velho e lapide. */
    public long bytesNoDisco() {
        long bytes = 0;

        for (List<Arquivo> nivel : niveis)
            for (Arquivo arquivo : nivel) bytes += arquivo.bytes();

        return bytes;
    }

    /** Quantas entradas existem nos arquivos, contando as repetidas em niveis diferentes. */
    public long entradasNoDisco() {
        long quantas = 0;

        for (List<Arquivo> nivel : niveis)
            for (Arquivo arquivo : nivel) quantas += arquivo.quantasChaves();

        return quantas;
    }

    /** Quantas lapides ainda estao ocupando lugar. */
    public long lapides() {
        long quantas = 0;

        for (List<Arquivo> nivel : niveis)
            for (Arquivo arquivo : nivel)
                for (int i = 0; i < arquivo.quantasChaves(); i++)
                    if (arquivo.valorNaPosicao(i).apagado()) quantas++;

        return quantas;
    }

    /** Quantos pares de arquivos do MESMO nivel tem faixas que se cruzam. */
    public int cruzamentosNoMesmoNivel() {
        int quantos = 0;

        for (int nivel = 1; nivel < niveis.size(); nivel++) {
            List<Arquivo> arquivos = niveis.get(nivel);

            for (int i = 0; i < arquivos.size(); i++)
                for (int j = i + 1; j < arquivos.size(); j++)
                    if (arquivos.get(i).seCruzaCom(arquivos.get(j))) quantos++;
        }

        return quantos;
    }

    public Politica politica() {
        return politica;
    }

    public boolean temFiltro() {
        return comFiltro;
    }
}
