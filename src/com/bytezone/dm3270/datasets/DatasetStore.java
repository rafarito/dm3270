package com.bytezone.dm3270.datasets;

/**
 * Porta de persistencia de datasets e membros observados na tela.
 * Use {@link #NONE} quando nao houver banco configurado.
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
