import { ComponentFixture, TestBed } from '@angular/core/testing';

import { QuienesSomosComponent } from './quienes-somos';

// Prueba básica: el componente se crea sin errores.
describe('QuienesSomos', () => {
  let component: QuienesSomosComponent;
  let fixture: ComponentFixture<QuienesSomosComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QuienesSomosComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(QuienesSomosComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
