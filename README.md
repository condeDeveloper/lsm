# lsm

Árvore de mesclagem estruturada em log do zero em Java 21: compactação por nível contra
compactação por tamanho, com as três amplificações medidas.

O juiz é um **`TreeMap` espelho**. Toda operação vai para os dois ao mesmo tempo, e no fim
o juiz confere **todas as chaves que já existiram**, inclusive as apagadas.

Essa escolha é o repositório inteiro. Uma árvore de mesclagem que só é conferida nas
chaves que o roteiro acabou de ler passa com dois erros graves dentro, e passou: os dois
que estão na última seção.

Escrever só no fim e nunca por cima é barato. O preço é que o mesmo dado passa a existir
em vários lugares, e daí saem três números que **não dá para melhorar ao mesmo tempo**.

## As três amplificações lado a lado

```
$ mvn exec:java -Dexec.args="troca"

  politica           filtro          escrita        leitura de ausentes         espaco     arquivos     niveis cruzamentos no mesmo nivel
  por nivel             nao             9.25                       4.72           1.31            5          2                          0
  por tamanho           nao             2.01                       7.74           4.44            8          3                          9
  por nivel             sim             9.25                       0.04           1.31            5          2                          0
  por tamanho           sim             2.01                       0.05           4.44            8          3                          9
```

Cada política é melhor em duas colunas e pior na terceira. A por nível escreve **4,6 vezes
mais** e ocupa **3,4 vezes menos** disco.

A última coluna explica a do meio. Na compactação por nível os arquivos de um mesmo nível
não têm faixas que se cruzam, então uma leitura olha no máximo um arquivo por nível. Na
por tamanho elas se cruzam, e a leitura tem de olhar todos.

## A ordem das chaves decide a amplificação de escrita

```
$ mvn exec:java -Dexec.args="escrita"

  roteiro              politica         bytes do usuario     bytes no disco     amplificacao   compactacoes     niveis
  sempre novas         por nivel                 1060000           50413600            47.56             96          2
  em ordem             por nivel                 1060000            2671200             2.52            152          3
  sempre novas         por tamanho               1060000            3180000             3.00             24          3
  em ordem             por tamanho               1060000            3180000             3.00             24          3
```

As mesmas 20.000 chaves, os mesmos 1.060.000 bytes. A única diferença é a **ordem** em que
elas chegam, e a amplificação de escrita vai de **2,52 para 47,56**.

Compactar por nível mescla um arquivo com todos os de baixo cuja faixa toca a dele. Com
chaves em ordem, cada arquivo novo toca um arquivo só. Com chaves embaralhadas, cada
arquivo novo cobre a faixa inteira e toca todo mundo.

A compactação por tamanho não se importa: 3,00 nos dois.

## Apagar é escrever

```
$ mvn exec:java -Dexec.args="espaco"

  roteiro              politica         chaves vivas      bytes vivos   bytes no disco   amplificacao    lapides    entradas no disco
  sempre novas         por nivel               20000          1060000          1060000           1.00          0                20000
  repetidas em 1000    por nivel                1000            53000            93121           1.76          0                 1757
  repetidas em 1000    por tamanho              1000            53000           265689           5.01          0                 5013
  com apagamentos      por nivel                1000            37736            70114           1.86        223                 1487
  com apagamentos      por tamanho              1000            37736           202169           5.36       1202                 4699
```

Não dá para ir ao arquivo antigo e tirar a chave de lá, porque ele é imutável. Escreve-se
uma **lápide** por cima, e a chave apagada passa a ocupar **mais** lugar do que ocupava.

Com 1.000 chaves vivas, a compactação por tamanho carrega **1.202 lápides**: mais lápides
do que chaves.

## A chave que não existe custa mais que a que existe

```
$ mvn exec:java -Dexec.args="leitura"

  politica           filtro o que le         arquivos   arquivos por leitura     buscas por leitura    buscas em vao  descartes pelo filtro
  por nivel             nao existentes              5                   4.39                   4.12            15607                      0
  por nivel             nao ausentes                5                   5.00                   4.72            23624                      0
  por nivel             sim ausentes                5                   5.00                   0.04              214                  23410
  por tamanho           nao ausentes                8                   8.00                   7.74            38721                      0
  por tamanho           sim ausentes                8                   8.00                   0.05              266                  38455
```

Achar uma chave que existe custa olhar os arquivos **até ela aparecer**, e ela costuma
aparecer cedo porque o mais recente está mais em cima. Descobrir que uma chave **não**
existe custa olhar **todos**, até o último, para ter certeza: 5,00 de 5 e 8,00 de 8.

O filtro de Bloom corta a busca binária em vão de 4,72 para 0,04 por leitura, e não mexe
em quantos arquivos foram considerados. Ele não evita olhar: evita abrir.

## Como rodar

```
mvn test
mvn exec:java -Dexec.args="tudo"
```

Os comandos do medidor são `correcao`, `escrita`, `leitura`, `espaco`, `troca` e `tudo`.

## O que as medidas me corrigiram

**A compactação por tamanho ressuscitava chaves apagadas.**

Uma lápide só pode sumir quando não houver mais nenhum arquivo **abaixo** capaz de ter a
chave viva. Eu escrevi essa condição uma vez, como "o nível de destino é o último", e usei
a mesma nas duas políticas.

Ela vale na compactação por nível: os arquivos que sobram no último nível têm faixas que
não tocam a do arquivo que desceu, então nenhum deles pode ter a chave. Na por tamanho não
vale, porque ali as faixas se cruzam. A lápide sumia, o valor antigo reaparecia, e o
espelho pegou **620 divergências**.

**E o valor mais antigo ganhava a mesclagem.**

Mesclar vários arquivos num só é pôr tudo num mapa ordenado, e o valor que vale é o do
arquivo **mais novo**: ele precisa ser o último a entrar.

As duas políticas montavam a lista de arquivos em ordens de idade **opostas**. Na por
nível, o arquivo que desce vinha na frente, e o laço andava para trás. Na por tamanho, a
lista chegava do mais antigo para o mais novo, e o mesmo laço fazia o contrário do que
devia. Mais **427 divergências**, e nenhuma delas aparecia na política por nível.

Os dois erros estavam juntos e um escondia o outro. Depois de consertar o primeiro, as
divergências caíram de 620 para 427 e eu quase dei por encerrado.

Nenhum dos dois apareceria num teste que lesse só o que acabou de escrever: os dois só
quebram chaves que foram apagadas ou reescritas há muitos níveis.

**As minhas chaves ausentes não estavam ausentes: estavam fora da faixa.**

A tabela da leitura existe para medir o que custa procurar uma chave que não existe, e o
roteiro delas gerava chaves **acima** de todas as escritas. Com isso, todo arquivo era
descartado pela faixa de chaves antes de qualquer busca.

A tabela dava **zero busca por leitura** nas quatro linhas, e o filtro de Bloom aparecia
sem servir para nada. Chave ausente de verdade cai no **meio** das que existem, e os
roteiros passaram a escrever números pares e procurar ímpares.

**O filtro de Bloom apaga a diferença de leitura entre as duas políticas.**

Era para a tabela das três amplificações ter uma linha por política. Ficou com duas, com e
sem filtro, porque elas contam coisas diferentes.

Sem filtro, a por tamanho busca 7,74 vezes por leitura e a por nível busca 4,72: a leitura
é um critério de escolha de verdade. Com filtro, as duas caem para 0,05 e **a coluna do
meio para de separar qualquer coisa**. A escolha entre as duas políticas passa a ser só
entre escrita e espaço, e dez bits por chave compraram isso.

## Licença

MIT.
