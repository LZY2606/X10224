package dsc.store;

import com.fasterxml.jackson.databind.ObjectMapper;

final class ObjectMapperHolder {
    final ObjectMapper mapper;

    ObjectMapperHolder(ObjectMapper mapper) {
        this.mapper = mapper;
    }
}
