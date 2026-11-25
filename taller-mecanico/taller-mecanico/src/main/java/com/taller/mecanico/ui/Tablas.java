package com.taller.mecanico.ui;

import com.taller.mecanico.model.EstadoOrden;
import com.taller.mecanico.model.Repuesto;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Consumer;

public final class Tablas {
    public static final Locale LOCALE = Locale.forLanguageTag("es-PE");
    public static final NumberFormat MONEDA = NumberFormat.getCurrencyInstance(LOCALE);

    private Tablas() {
    }

    public static <T> void configurar(TableView<T> tabla, String textoVacio, Consumer<T> alDobleClic) {
        configurar(tabla, Feather.INBOX, "Sin resultados", textoVacio, alDobleClic);
    }

    public static <T> void configurar(TableView<T> tabla, Ikon iconoVacio, String tituloVacio, String textoVacio,
                                      Consumer<T> alDobleClic) {
        tabla.getStyleClass().add("tabla");
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        FontIcon icono = new FontIcon(iconoVacio);
        icono.getStyleClass().add("vacio-icono");
        Label titulo = new Label(tituloVacio);
        titulo.getStyleClass().add("vacio-titulo");
        Label detalle = new Label(textoVacio);
        detalle.getStyleClass().add("vacio-detalle");
        VBox vacio = new VBox(8, icono, titulo, detalle);
        vacio.setAlignment(Pos.CENTER);
        tabla.setPlaceholder(vacio);

        tabla.setRowFactory(tv -> {
            TableRow<T> fila = new TableRow<>();
            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !fila.isEmpty()) {
                    alDobleClic.accept(fila.getItem());
                }
            });
            return fila;
        });
    }

    public static <T> Callback<TableColumn<T, String>, TableCell<T, String>> celdaId() {
        return col -> new TableCell<>() {
            {
                getStyleClass().add("celda-id");
            }

            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio || valor == null ? null : "#" + valor);
            }
        };
    }

    public static <T> Callback<TableColumn<T, String>, TableCell<T, String>> celdaAvatar() {
        return col -> new TableCell<>() {
            private final Label iniciales = new Label();
            private final StackPane avatar = new StackPane(iniciales);
            private final Label nombre = new Label();
            private final HBox caja = new HBox(10, avatar, nombre);

            {
                avatar.getStyleClass().add("avatar");
                caja.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                if (vacio || valor == null) {
                    setGraphic(null);
                    return;
                }
                iniciales.setText(iniciales(valor));
                nombre.setText(valor);
                setGraphic(caja);
            }
        };
    }

    public static <T> Callback<TableColumn<T, String>, TableCell<T, String>> celdaChip(String textoVacio) {
        return col -> new TableCell<>() {
            private final Label chip = new Label();

            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                if (vacio) {
                    setGraphic(null);
                    return;
                }
                boolean sinValor = valor == null || valor.isBlank();
                chip.setText(sinValor ? textoVacio : valor);
                chip.getStyleClass().setAll("chip");
                if (sinValor) {
                    chip.getStyleClass().add("neutro");
                }
                setGraphic(chip);
            }
        };
    }

    public static <T> Callback<TableColumn<T, BigDecimal>, TableCell<T, BigDecimal>> celdaMoneda() {
        return col -> new TableCell<>() {
            {
                getStyleClass().add("celda-importe");
            }

            @Override
            protected void updateItem(BigDecimal valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio || valor == null ? null : MONEDA.format(valor));
            }
        };
    }

    public static <T> Callback<TableColumn<T, String>, TableCell<T, String>> celdaOpcional() {
        return col -> new TableCell<>() {
            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                boolean sinValor = !vacio && (valor == null || valor.isBlank());
                setText(vacio ? null : sinValor ? "—" : valor);
                pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("sin-valor"), sinValor);
            }
        };
    }

    public static <T> Callback<TableColumn<T, String>, TableCell<T, String>> celdaEstado() {
        return col -> new TableCell<>() {
            private final Label chip = new Label();

            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                if (vacio || valor == null) {
                    setGraphic(null);
                    return;
                }
                chip.setText(valor);
                chip.getStyleClass().setAll("chip", "ANULADA".equals(valor) ? "peligro" : "exito");
                setGraphic(chip);
            }
        };
    }

    public static <T> Callback<TableColumn<T, Repuesto>, TableCell<T, Repuesto>> celdaStock() {
        return col -> new TableCell<>() {
            private final Label chip = new Label();

            @Override
            protected void updateItem(Repuesto p, boolean vacio) {
                super.updateItem(p, vacio);
                if (vacio || p == null) {
                    setGraphic(null);
                    return;
                }
                chip.setText(p.isSinStock() ? "Sin stock" : p.getStock() + " u.");
                chip.getStyleClass().setAll("chip", claseStock(p));
                setGraphic(chip);
            }
        };
    }

    public static String claseStock(Repuesto p) {
        return p.isSinStock() ? "peligro" : p.isStockBajo() ? "advertencia" : "exito";
    }

    public static <T> Callback<TableColumn<T, EstadoOrden>, TableCell<T, EstadoOrden>> celdaEstadoOrden() {
        return col -> new TableCell<>() {
            private final Label chip = new Label();

            @Override
            protected void updateItem(EstadoOrden estado, boolean vacio) {
                super.updateItem(estado, vacio);
                if (vacio || estado == null) {
                    setGraphic(null);
                    return;
                }
                aplicarEstilo(chip, estado);
                setGraphic(chip);
            }
        };
    }

    public static void aplicarEstilo(Label chip, EstadoOrden estado) {
        chip.setText(estado.getDescripcion());
        chip.getStyleClass().setAll("chip", claseEstado(estado));
    }

    public static String claseEstado(EstadoOrden estado) {
        return switch (estado) {
            case RECIBIDO -> "neutro";
            case EN_DIAGNOSTICO, EN_REPARACION -> "advertencia";
            case LISTO -> "listo";
            case ENTREGADO -> "exito";
            case CANCELADO -> "peligro";
        };
    }

    public static <T> Callback<TableColumn<T, Boolean>, TableCell<T, Boolean>> celdaActivo() {
        return col -> new TableCell<>() {
            private final Label chip = new Label();

            @Override
            protected void updateItem(Boolean activo, boolean vacio) {
                super.updateItem(activo, vacio);
                if (vacio || activo == null) {
                    setGraphic(null);
                    return;
                }
                chip.setText(activo ? "Activo" : "Inactivo");
                chip.getStyleClass().setAll("chip", activo ? "exito" : "neutro");
                setGraphic(chip);
            }
        };
    }

    public static String iniciales(String texto) {
        String[] partes = texto.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length && sb.length() < 2; i++) {
            if (!partes[i].isEmpty()) {
                sb.append(Character.toUpperCase(partes[i].charAt(0)));
            }
        }
        return sb.toString();
    }
}
