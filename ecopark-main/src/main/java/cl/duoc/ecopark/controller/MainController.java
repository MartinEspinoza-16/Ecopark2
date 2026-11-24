package cl.duoc.ecopark.controller;

import cl.duoc.ecopark.dao.MySqlEstadiaDAO;
import cl.duoc.ecopark.model.Estadia;
import cl.duoc.ecopark.model.EstadiaAuto;
import cl.duoc.ecopark.model.EstadiaMoto;
import cl.duoc.ecopark.model.ResumenTipo;
import cl.duoc.ecopark.repository.EstadiaRepository;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class MainController {

    @FXML private ComboBox<String> tipoCombo;
    @FXML private TextField patenteField;
    @FXML private TextField horasField;
    @FXML private CheckBox convenioCheck;

    @FXML private TextField buscarPatenteField;
    @FXML private TextField nuevasHorasField;

    @FXML private TableView<Estadia> estadiasTable;
    @FXML private TableColumn<Estadia, Number> idCol;
    @FXML private TableColumn<Estadia, String> tipoCol;
    @FXML private TableColumn<Estadia, String> patenteCol;
    @FXML private TableColumn<Estadia, Number> horasCol;
    @FXML private TableColumn<Estadia, String> costoCol;

    @FXML private Label mensajeLabel;
    @FXML private Label estadisticasLabel;

    private final ObservableList<Estadia> filas =
            FXCollections.observableArrayList();

    private final EstadiaRepository repository =
            new EstadiaRepository(
                    new MySqlEstadiaDAO()
            );

    private final NumberFormat formatoPesos =
            NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CL"));

    @FXML
    private void initialize() {
        tipoCombo.getItems().addAll("Auto", "Moto");
        tipoCombo.getSelectionModel().selectFirst();

        tipoCombo.valueProperty().addListener((obs, antes, despues) ->
                actualizarConvenio());
        actualizarConvenio();
        horasField.getTextFormatter(soloNumeros());
        nuevasHorasField.setTextFormatter(soloNumeros());

        idCol.setCellValueFactory(dato -> new ReadOnlyLongWrapper(dato.getValue().getId()));
        tipoCol.setCellValueFactory(dato -> new ReadOnlyStringWrapper(dato.getValue().getTipo()));
        patenteCol.setCellValueFactory(dato -> new ReadOnlyStringWrapper(dato.getValue().getPatente()));
        horasCol.setCellValueFactory(dato -> new ReadOnlyIntegerWrapper(dato.getValue().getHoras()));
        costoCol.setCellValueFactory(dato -> new ReadOnlyStringWrapper(formatearCosto(dato.getValue().calcularCosto())));

        estadiasTable.setItems(filas);

        estadiasTable.getSelectionModel().selectedItemProperty().addListener((obs, anterior, actual) -> {
            if (anterior != null) {
                nuevasHorasField.setText(String.valueOf(anterior.getHoras()));
            }
        });

        try {
            repository.cargar();
            actualizarTabla();
            mostrarMensaje("Estadías cargadas: " + filas.size() + ".", false);
        } catch (IOException | RuntimeException e) {
            mostrarMensaje("No se pudieron cargar los datos: " + e.getMessage(), true);
        }
    }

    private TextFormatter<String> soloNumeros(){
        return new TextFormatter<>(cambio -> cambio.getControlText().matches("[0-9]*") ? cambio :null);
    }



    private void actualizarConvenio() {
        boolean esMoto = "Moto".equals(tipoCombo.getValue());
        convenioCheck.setDisable(!esMoto);
        if (!esMoto) {
            convenioCheck.setSelected(false);
        }
    }
    @FXML
    private void actualizarHoras(){

    }
    @FXML
    private void buscarPorPatente(){}
    @FXML
    private void mostrarTodos(){
        mostrarTodosSinMensaje();
        buscarPatenteField.clear();
        mostrarTodosSinMensaje("Mostrando todas las estadias", false);
    }
    @FXML
    private void mostrarEstadisticas()  {
        try {
            List<ResumenTipo> datos = repository.resumenPorTipo();
            if(datos.isEmpty()){
                estadisticasLabel.setText("No se encuentran estadisticas para eliminar.");
                return;
            }
            StringBuilder texto = new StringBuilder("Estadisticas: ");
        }catch (IOException e){
            estadisticasLabel.setText("No se encuentran estadisticas.");
            long total = 0;
            for (ResumenTipo resumen : datos){
                if(total > 0) texto.append(" | ");
                texto.append(resumen.tipo()).append(":")
                        .append(resumen.cantidad());
            }
            texto.append(" | Total: ").append(total);
            estadisticasLabel.setText(texto.toString());
        }
    }

    @FXML
    private void registrar() {
        try {
            String tipo = tipoCombo.getValue();
            String patente = patenteField.getText().trim();
            int horas = Integer.parseInt(nuevasHorasField.getText());
            Estadia nueva;
            if (tipo == null) {
                throw new IllegalArgumentException("Seleccione Auto o Moto.");
            }
            if (patente.isEmpty()) {
                throw new IllegalArgumentException("La patente es obligatoria.");
            }


            if ("Auto".equals(tipo)) {
                nueva = new EstadiaAuto(patente, horas);
            } else if ("Moto".equals(tipo)) {
                nueva = new EstadiaMoto(patente, horas, convenioCheck.isSelected());
            } else {
                throw new IllegalArgumentException("Tipo de vehículo desconocido.");
            }

            repository.registrar(nueva);
            actualizarTabla();
            limpiarFormulario();
            mostrarMensaje(nueva.getTipo() + " " + nueva.getPatente()
                    + " registrada. Costo: " + formatearCosto(nueva.calcularCosto()), false);

        } catch (NumberFormatException e) {
            mostrarMensaje("Las horas deben ser un número entero entre 1 y 24.", true);
        } catch (IllegalArgumentException e) {
            mostrarMensaje(e.getMessage(), true);
        } catch (IOException e) {
            mostrarMensaje("No se pudo registrar en MySQL: " + e.getMessage(), true);
        }
    }

    @FXML
    private void eliminar() {
        Estadia seleccionada = estadiasTable.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            mostrarMensaje("Seleccione una estadía de la tabla para eliminar.", true);
            return;
        }

        try {
            repository.eliminar(seleccionada);
            //MopstrarTodosSinMensaje();
            //IndicarEstadisticasPendientes();
            actualizarTabla();
            mostrarMensaje("Estadía " + seleccionada.getPatente() + " eliminada.", false);
        } catch (IllegalArgumentException e) {
            mostrarMensaje(e.getMessage(), true);
        } catch (IOException e) {
            mostrarMensaje("No se pudo eliminar en MySQL: " + e.getMessage(), true);
        }
    }

    private void actualizarTabla() {
        filas.setAll(repository.listar());
    }

    private void limpiarFormulario() {
        patenteField.clear();
        horasField.clear();
        convenioCheck.setSelected(false);
        tipoCombo.getSelectionModel().selectFirst();
        patenteField.requestFocus();
    }
    public void mostrarTodosSinMensaje() {
        filas.setAll(repository.listar());
        estadiasTable.getSelectionModel().clearSelection();
        nuevasHorasField.clear();
    }
    private void indicarEstadisticasPendienters(){
        estadisticasLabel.setText("Estadisticas: pulsa el boton para actualizar");
    }

    private void limpiarRegistros(){}

    private void mostrarMensaje(String mensaje, boolean error) {
        mensajeLabel.setText(mensaje);
        mensajeLabel.getStyleClass().removeAll("mensaje-error", "mensaje-exito");
        mensajeLabel.getStyleClass().add(error ? "mensaje-error" : "mensaje-exito");
    }

    private String formatearCosto(int costo) {
        return "$" + formatoPesos.format(costo);
    }




}