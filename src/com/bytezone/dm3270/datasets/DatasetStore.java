package com.bytezone.dm3270.datasets;

/*
 * Onde os datasets e membros observados na tela vao parar.
 *
 * O FieldManager - cuja responsabilidade e agrupar posicoes de tela em campos - abria o
 * arquivo do banco, subia uma thread e enfileirava um OPEN dentro do proprio construtor. O
 * ScreenWatcher montava DatasetRequest e MemberRequest e os empurrava para a mesma fila. As
 * duas classes conheciam a thread, a fila, os quatro tipos de request e o enum de comandos.
 *
 * Este contrato e o que elas realmente precisam: abrir, fechar e gravar o que a tela mostrou.
 * A fila, a thread e o SQL ficam do outro lado, no QueuedDatasetStore, e o ciclo de vida
 * passa a ser do composition root - quem constroi decide se ha banco ou nao, em vez de o
 * FieldManager decidir por um `if (serverSite != null)`.
 *
 * NONE e o caso sem banco. Antes ele era representado por uma fila nula, com um teste de nulo
 * em cada ponto de envio; agora e um objeto que nao faz nada, com o mesmo efeito observavel.
 */
// -----------------------------------------------------------------------------------//
public interface DatasetStore
// -----------------------------------------------------------------------------------//
{
  void open (StoreListener listener);

  void close (StoreListener listener);

  void update (Dataset dataset);

  void update (Member member);

  // ---------------------------------------------------------------------------------//
  DatasetStore NONE = new DatasetStore ()
  // ---------------------------------------------------------------------------------//
  {
    @Override
    public void open (StoreListener listener)
    {
    }

    @Override
    public void close (StoreListener listener)
    {
    }

    @Override
    public void update (Dataset dataset)
    {
    }

    @Override
    public void update (Member member)
    {
    }
  };
}
