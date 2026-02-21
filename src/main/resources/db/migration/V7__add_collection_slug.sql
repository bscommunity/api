ALTER TABLE collections ADD slug VARCHAR(30) NULL;
ALTER TABLE collections ADD CONSTRAINT collections_slug_unique UNIQUE (slug);
