module com.dotvault {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires javafx.web;
    requires java.desktop;
    
    exports com.dotvault;
    exports com.dotvault.controller;
    exports com.dotvault.model;
    exports com.dotvault.service;
    exports com.dotvault.util;
    
    opens com.dotvault.controller to javafx.fxml;
    opens com.dotvault.model to javafx.base;
}
