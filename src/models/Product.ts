import { Schema, model, Document } from 'mongoose';

export interface IProduct extends Document {
  p_id: string;
  p_name: string;
  createdAt: Date;
  updatedAt: Date;
}

const ProductSchema = new Schema<IProduct>(
  {
    p_id: {
      type: String,
      required: [true, 'Product ID is required'],
      unique: true,
      trim: true,
      uppercase: true
    },
    p_name: {
      type: String,
      required: [true, 'Product name is required'],
      trim: true
    }
  },
  {
    timestamps: true,
    collection: 'products'
  }
);

// Index for faster queries
ProductSchema.index({ p_id: 1 });

export const Product = model<IProduct>('Product', ProductSchema);




