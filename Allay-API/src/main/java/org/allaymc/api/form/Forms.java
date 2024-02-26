package org.allaymc.api.form;

import org.allaymc.api.form.type.CustomForm;
import org.allaymc.api.form.type.ModalForm;
import org.allaymc.api.form.type.SimpleForm;

/**
 * Allay Project 2024/2/26
 *
 * @author daoge_cmd
 */
public interface Forms {
    static SimpleForm simple() {
        return new SimpleForm();
    }

    static ModalForm modal() {
        return new ModalForm();
    }

    static CustomForm custom() {
        return new CustomForm();
    }
}
