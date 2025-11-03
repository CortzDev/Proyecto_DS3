package App;

import Model.Tienda;
import Blockchain.BlockChainStore;

import javax.swing.SwingUtilities;

public class AppContext {
    public final Tienda tienda;
    public final BlockChainStore blockchainStore;

    private Runnable refresher = () -> {};

    public AppContext(Tienda tienda, BlockChainStore blockchainStore) {
        this.tienda = tienda;
        this.blockchainStore = blockchainStore;
    }

    public void setRefresher(Runnable r) {
        this.refresher = (r != null) ? r : () -> {};
    }

    public Runnable getRefresher() {
        return refresher;
    }

    public void refreshAllViews() {
        try { refresher.run(); } catch (Throwable ignored) {}
    }

    public void refreshAllViewsLater() {
        SwingUtilities.invokeLater(() -> {
            try { refresher.run(); } catch (Throwable ignored) {}
        });
    }
}
