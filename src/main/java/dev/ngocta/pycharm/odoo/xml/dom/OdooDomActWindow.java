package dev.ngocta.pycharm.odoo.xml.dom;

import dev.ngocta.pycharm.odoo.OdooNames;
import dev.ngocta.pycharm.odoo.data.OdooRecord;

public interface OdooDomActWindow extends OdooDomRecordLike {
    @Override
    default OdooRecord getRecord() {
        return getRecord(OdooNames.IR_ACTIONS_ACT_WINDOW, null);
    }
}
